package ru.abs7.b24support.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.ticket.SupportTicketListResponse;
import ru.abs7.b24support.api.dto.ticket.SupportTicketResponse;
import ru.abs7.b24support.api.dto.ticket.TicketActionResponse;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.BitrixUser;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.SupportMessage;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.domain.SupportTicketStatus;
import ru.abs7.b24support.repo.BitrixUserRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import ru.abs7.b24support.repo.SupportMessageRepository;
import ru.abs7.b24support.repo.SupportTicketRepository;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SupportTicketService {

    private static final Collection<SupportTicketStatus> ACTIVE_STATUSES =
            List.of(SupportTicketStatus.OPENING, SupportTicketStatus.OPEN);

    private final SupportTicketRepository ticketRepository;
    private final PortalInstallationRepository portalRepository;
    private final BitrixUserRepository bitrixUserRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SupportSettingsService settingsService;
    private final BitrixRestClient bitrixRestClient;
    private final CrmTicketSyncService crmTicketSyncService;

    public SupportTicketService(SupportTicketRepository ticketRepository,
                                PortalInstallationRepository portalRepository,
                                BitrixUserRepository bitrixUserRepository,
                                SupportMessageRepository supportMessageRepository,
                                SupportSettingsService settingsService,
                                BitrixRestClient bitrixRestClient,
                                CrmTicketSyncService crmTicketSyncService) {
        this.ticketRepository = ticketRepository;
        this.portalRepository = portalRepository;
        this.bitrixUserRepository = bitrixUserRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.settingsService = settingsService;
        this.bitrixRestClient = bitrixRestClient;
        this.crmTicketSyncService = crmTicketSyncService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized TicketResolution resolveOrCreateOpenTicket(PortalInstallation client, String clientDialogId) {
        requireClientDialog(clientDialogId);

        SupportTicket existing = ticketRepository
                .findFirstByClientInstallation_IdAndStatusInOrderByIdDesc(client.getId(), ACTIVE_STATUSES)
                .orElse(null);

        if (existing != null && existing.getStatus() == SupportTicketStatus.OPEN) {
            existing.updateClientDialogId(clientDialogId);
            return new TicketResolution(ticketRepository.save(existing), false);
        }

        if (existing != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Для клиента уже создаётся новый чат обращения. Повтори отправку через несколько секунд"
            );
        }

        PortalInstallation admin = findReadyAdminPortal();
        List<Integer> supportUserIds = supportBitrixUserIds(admin);
        if (supportUserIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В админке не выбраны специалисты техподдержки");
        }

        long sequenceNumber = ticketRepository
                .findFirstByClientInstallation_IdOrderByClientSequenceNumberDesc(client.getId())
                .map(previous -> previous.getClientSequenceNumber() + 1)
                .orElse(1L);
        String chatTitle = cleanChatTitle(client.getTitle(), sequenceNumber);
        SupportTicket ticket = ticketRepository.saveAndFlush(
                new SupportTicket(client, clientDialogId, chatTitle, sequenceNumber)
        );

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", chatTitle);
        fields.put("description", "Обращение техподдержки #" + ticket.getId() + " от " + client.getDomain());
        fields.put("color", "mint");
        fields.put("userIds", supportUserIds);
        fields.put("message", "Создано новое обращение #" + ticket.getId() + ". Все сообщения в этом чате отправляются клиенту.");

        Map<String, Object> payload = Map.of(
                "botId", parseInteger(admin.getBotId(), "Admin Bot ID"),
                "botToken", admin.getBotToken(),
                "fields", fields
        );

        JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.add", payload);
        JsonNode chat = root.path("result").path("chat");
        String chatId = nodeText(chat, "id");
        String adminDialogId = nodeText(chat, "dialogId");
        if (adminDialogId == null) {
            throw new BitrixRestException("Bitrix24 не вернул dialogId нового чата обращения");
        }

        ticket.markOpen(chatId, adminDialogId);
        SupportTicket saved = ticketRepository.save(ticket);
        crmTicketSyncService.syncTicket(saved);
        return new TicketResolution(saved, true);
    }

    public String forwardClientMessage(SupportTicket ticket, SupportMessage message) {
        requireOpen(ticket);
        PortalInstallation admin = findReadyAdminPortal();

        String text = "[B]" + safe(message.getSenderName(), "Клиент") + ":[/B]\n" + message.getText();
        Map<String, Object> payload = Map.of(
                "botId", parseInteger(admin.getBotId(), "Admin Bot ID"),
                "botToken", admin.getBotToken(),
                "dialogId", ticket.getAdminDialogId(),
                "fields", Map.of(
                        "message", text,
                        "urlPreview", false
                )
        );

        JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.Message.send", payload);
        return extractMessageId(root);
    }

    @Transactional(readOnly = true)
    public SupportTicket findOpenByAdminChat(String dialogId, String chatId) {
        SupportTicket ticket = null;
        if (dialogId != null && !dialogId.isBlank()) {
            ticket = ticketRepository.findFirstByAdminDialogIdOrderByIdDesc(dialogId.trim()).orElse(null);
        }
        if (ticket == null && chatId != null && !chatId.isBlank()) {
            ticket = ticketRepository.findFirstByAdminChatIdOrderByIdDesc(chatId.trim()).orElse(null);
        }
        return ticket != null && ticket.getStatus() == SupportTicketStatus.OPEN ? ticket : null;
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse get(Long ticketId) {
        return SupportTicketResponse.from(findTicket(ticketId));
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getByDialogId(String dialogId) {
        SupportTicket ticket = ticketRepository.findFirstByAdminDialogIdOrderByIdDesc(dialogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Обращение для этого чата не найдено"));
        return SupportTicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public SupportTicketListResponse recent() {
        List<SupportTicketResponse> items = ticketRepository.findTop100ByOrderByOpenedAtDesc()
                .stream()
                .map(SupportTicketResponse::from)
                .toList();
        return new SupportTicketListResponse(items.size(), items);
    }

    @Transactional
    public TicketActionResponse close(Long ticketId, String userId, String userName) {
        SupportTicket ticket = findTicket(ticketId);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED || ticket.getStatus() == SupportTicketStatus.DELETING) {
            return success("Обращение уже закрыто", ticket);
        }
        if (ticket.getStatus() == SupportTicketStatus.DELETED) {
            return success("Обращение уже закрыто, чат удалён", ticket);
        }
        if (ticket.getStatus() != SupportTicketStatus.OPEN) {
            return failure("Закрыть можно только открытое обращение", ticket);
        }

        try {
            String clientMessageId = sendClientClosedNotification(ticket);
            SupportMessage systemMessage = new SupportMessage(ticket.getClientInstallation());
            systemMessage.setSupportTicket(ticket);
            systemMessage.setDirection("SYSTEM_TO_CLIENT");
            systemMessage.setClientDialogId(ticket.getClientDialogId());
            systemMessage.setClientMessageId(clientMessageId);
            systemMessage.setAdminDialogId(ticket.getAdminDialogId());
            systemMessage.setSenderUserId(userId);
            systemMessage.setSenderName(safe(userName, "Специалист техподдержки"));
            systemMessage.setText("Ваше обращение выполнено и закрыто.");
            systemMessage.setStatus("SENT");
            supportMessageRepository.save(systemMessage);

            int retentionDays = settingsService.retentionDays();
            ticket.markClosed(userId, safe(userName, "Специалист техподдержки"), OffsetDateTime.now().plusDays(retentionDays));
            SupportTicket saved = ticketRepository.save(ticket);
            crmTicketSyncService.syncMessage(systemMessage);
            crmTicketSyncService.syncClosedStage(saved);
            return success("Обращение закрыто. Клиент уведомлён", saved);
        } catch (BitrixRestException | ResponseStatusException e) {
            return failure("Не удалось закрыть обращение: " + safe(e.getMessage(), "ошибка Bitrix24"), ticket);
        }
    }

    @Transactional
    public TicketActionResponse closeByDialogId(String dialogId, String userId, String userName) {
        SupportTicket ticket = ticketRepository.findFirstByAdminDialogIdOrderByIdDesc(dialogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Обращение для этого чата не найдено"));
        return close(ticket.getId(), userId, userName);
    }

    @Transactional
    public TicketActionResponse deleteChatByDialogId(String dialogId) {
        SupportTicket ticket = ticketRepository.findFirstByAdminDialogIdOrderByIdDesc(dialogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Обращение для этого чата не найдено"));
        return deleteChat(ticket.getId());
    }

    @Transactional
    public TicketActionResponse deleteChat(Long ticketId) {
        SupportTicket ticket = findTicket(ticketId);
        if (ticket.getStatus() == SupportTicketStatus.DELETED) {
            return success("Чат уже удалён", ticket);
        }
        if (ticket.getStatus() != SupportTicketStatus.CLOSED && ticket.getStatus() != SupportTicketStatus.DELETING) {
            return failure("Удалить можно только чат закрытого обращения", ticket);
        }

        PortalInstallation admin = findReadyAdminPortal();
        ticket.markDeleting();
        ticketRepository.save(ticket);

        try {
            removeAllHumanParticipants(admin, ticket);
            leaveChat(admin, ticket);
            ticket.markDeleted();
            return success("Чат обращения удалён у участников", ticketRepository.save(ticket));
        } catch (BitrixRestException | ResponseStatusException e) {
            ticket.markDeletionFailed(safe(e.getMessage(), "ошибка удаления чата"));
            return failure("Не удалось удалить чат: " + ticket.getLastError(), ticketRepository.save(ticket));
        }
    }

    @Transactional
    public int deleteExpiredChats() {
        List<SupportTicket> dueTickets = ticketRepository
                .findAllByStatusAndDeleteAfterLessThanEqualOrderByDeleteAfterAsc(SupportTicketStatus.CLOSED, OffsetDateTime.now());
        int deleted = 0;
        for (SupportTicket ticket : dueTickets) {
            TicketActionResponse result = deleteChat(ticket.getId());
            if (result.success()) {
                deleted++;
            }
        }
        return deleted;
    }

    private String sendClientClosedNotification(SupportTicket ticket) {
        PortalInstallation client = ticket.getClientInstallation();
        requireClientTarget(client, ticket.getClientDialogId());

        Map<String, Object> payload = Map.of(
                "botId", parseInteger(client.getBotId(), "Client Bot ID"),
                "botToken", client.getBotToken(),
                "dialogId", ticket.getClientDialogId(),
                "fields", Map.of(
                        "message", "Ваше обращение выполнено и закрыто.",
                        "urlPreview", false
                )
        );
        JsonNode root = bitrixRestClient.callJson(client.getWebhookUrl(), "imbot.v2.Chat.Message.send", payload);
        return extractMessageId(root);
    }

    private void removeAllHumanParticipants(PortalInstallation admin, SupportTicket ticket) {
        Map<String, Object> listPayload = Map.of(
                "botId", parseInteger(admin.getBotId(), "Admin Bot ID"),
                "botToken", admin.getBotToken(),
                "dialogId", ticket.getAdminDialogId(),
                "order", Map.of("id", "ASC"),
                "limit", 200
        );
        JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.User.list", listPayload);
        JsonNode users = root.path("result");
        if (!users.isArray()) {
            throw new BitrixRestException("Bitrix24 вернул неожиданный список участников чата");
        }

        Integer botId = parseInteger(admin.getBotId(), "Admin Bot ID");
        for (JsonNode user : users) {
            Integer userId = nodeInteger(user, "id");
            boolean isBot = user.path("bot").asBoolean(false);
            if (userId == null || isBot || Objects.equals(userId, botId)) {
                continue;
            }
            Map<String, Object> deletePayload = Map.of(
                    "botId", botId,
                    "botToken", admin.getBotToken(),
                    "dialogId", ticket.getAdminDialogId(),
                    "userId", userId
            );
            bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.User.delete", deletePayload);
        }
    }

    private void leaveChat(PortalInstallation admin, SupportTicket ticket) {
        Map<String, Object> payload = Map.of(
                "botId", parseInteger(admin.getBotId(), "Admin Bot ID"),
                "botToken", admin.getBotToken(),
                "dialogId", ticket.getAdminDialogId()
        );
        bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.leave", payload);
    }

    private PortalInstallation findReadyAdminPortal() {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Админский портал не подключён"));
        if (admin.getWebhookUrl() == null || admin.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У админского портала не заполнен webhook");
        }
        if (admin.getBotId() == null || admin.getBotId().isBlank()
                || admin.getBotToken() == null || admin.getBotToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала создай админского бота");
        }
        return admin;
    }

    private List<Integer> supportBitrixUserIds(PortalInstallation admin) {
        return bitrixUserRepository
                .findAllByPortalInstallationIdAndSupportMemberTrueOrderByLastNameAscFirstNameAscIdAsc(admin.getId())
                .stream()
                .filter(BitrixUser::isActive)
                .map(BitrixUser::getBitrixUserId)
                .map(this::tryParseInteger)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private SupportTicket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Обращение не найдено"));
    }

    private void requireOpen(SupportTicket ticket) {
        if (ticket == null || ticket.getStatus() != SupportTicketStatus.OPEN
                || ticket.getAdminDialogId() == null || ticket.getAdminDialogId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Админский чат обращения не готов");
        }
    }

    private void requireClientTarget(PortalInstallation client, String dialogId) {
        if (client.getWebhookUrl() == null || client.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У клиентского портала не заполнен webhook");
        }
        if (client.getBotId() == null || client.getBotToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У клиентского портала не создан бот");
        }
        requireClientDialog(dialogId);
    }

    private void requireClientDialog(String dialogId) {
        if (dialogId == null || dialogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не найден dialogId клиентского чата");
        }
    }

    private String cleanChatTitle(String value, long sequenceNumber) {
        String suffix = ", обращение №" + sequenceNumber;
        String company = safe(value, "Клиент");
        int maxCompanyLength = Math.max(1, 255 - suffix.length());
        if (company.length() > maxCompanyLength) {
            company = company.substring(0, maxCompanyLength).trim();
        }
        return company + suffix;
    }

    private String extractMessageId(JsonNode root) {
        JsonNode result = root.path("result");
        String id = nodeText(result, "id");
        if (id != null) {
            return id;
        }
        if (!result.isMissingNode() && !result.isNull()) {
            String value = result.asString(null);
            return value == null || value.isBlank() ? null : value.trim();
        }
        return null;
    }

    private String nodeText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Integer nodeInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        return tryParseInteger(value.asString(null));
    }

    private Integer parseInteger(String value, String fieldName) {
        Integer parsed = tryParseInteger(value);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " не заполнен или некорректен");
        }
        return parsed;
    }

    private Integer tryParseInteger(String value) {
        try {
            return value == null ? null : Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private TicketActionResponse success(String message, SupportTicket ticket) {
        return new TicketActionResponse(true, message, SupportTicketResponse.from(ticket));
    }

    private TicketActionResponse failure(String message, SupportTicket ticket) {
        return new TicketActionResponse(false, message, SupportTicketResponse.from(ticket));
    }

    public record TicketResolution(SupportTicket ticket, boolean created) {
    }
}
