package ru.abs7.b24support.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.CrmCompanyMatchStatus;
import ru.abs7.b24support.domain.CrmIntegrationConfig;
import ru.abs7.b24support.domain.CrmSyncStatus;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.SupportMessage;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.domain.SupportTicketStatus;
import ru.abs7.b24support.repo.CrmIntegrationConfigRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import ru.abs7.b24support.repo.SupportMessageRepository;
import ru.abs7.b24support.repo.SupportTicketRepository;
import tools.jackson.databind.JsonNode;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CrmTicketSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(CrmTicketSyncService.class);
    private static final DateTimeFormatter COMMENT_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Collection<CrmSyncStatus> RETRY_STATUSES =
            List.of(CrmSyncStatus.PENDING, CrmSyncStatus.ERROR, CrmSyncStatus.NOT_CONFIGURED);

    private final PortalInstallationRepository portalRepository;
    private final CrmIntegrationConfigRepository configRepository;
    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final BitrixRestClient bitrixRestClient;

    public CrmTicketSyncService(PortalInstallationRepository portalRepository,
                                CrmIntegrationConfigRepository configRepository,
                                SupportTicketRepository ticketRepository,
                                SupportMessageRepository messageRepository,
                                BitrixRestClient bitrixRestClient) {
        this.portalRepository = portalRepository;
        this.configRepository = configRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.bitrixRestClient = bitrixRestClient;
    }

    @Transactional
    public void syncTicket(SupportTicket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        CrmIntegrationConfig config = enabledConfig();
        if (config == null) {
            ticket.markCrmNotConfigured();
            ticketRepository.save(ticket);
            return;
        }

        PortalInstallation admin = config.getAdminPortal();
        if (admin.getWebhookUrl() == null || admin.getWebhookUrl().isBlank()) {
            ticket.markCrmError("У админского портала не заполнен Webhook / REST URL", CrmCompanyMatchStatus.ERROR);
            ticketRepository.save(ticket);
            return;
        }

        try {
            if (ticket.getCrmItemId() == null) {
                createCrmItem(ticket, config, admin);
            }
            if (ticket.getCrmItemId() != null && isLocallyClosed(ticket) && ticket.getCrmClosedAt() == null) {
                moveToClosedStage(ticket, config, admin);
            }
        } catch (RuntimeException e) {
            String error = safeError(e, "Не удалось синхронизировать тикет со смарт-процессом");
            ticket.markCrmError(error, ticket.getCrmCompanyMatchStatus());
            ticketRepository.save(ticket);
            LOG.warn("CRM ticket sync failed: ticketId={}, error={}", ticket.getId(), error);
        }
    }

    @Transactional
    public void syncMessage(SupportMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        if (message.getCrmTimelineCommentId() != null || message.getCrmSyncStatus() == CrmSyncStatus.SYNCED) {
            return;
        }
        if (isTechnicalSystemMessage(message)) {
            message.markCrmSkipped("Технические сообщения системы не добавляются в таймлайн CRM");
            messageRepository.save(message);
            return;
        }
        SupportTicket ticket = message.getSupportTicket();
        if (ticket == null) {
            message.markCrmSkipped("Сообщение не связано с обращением");
            messageRepository.save(message);
            return;
        }

        CrmIntegrationConfig config = enabledConfig();
        if (config == null) {
            message.markCrmNotConfigured();
            messageRepository.save(message);
            return;
        }

        if (ticket.getCrmItemId() == null) {
            syncTicket(ticket);
        }
        if (ticket.getCrmItemId() == null) {
            message.markCrmPending("CRM-тикет ещё не создан");
            messageRepository.save(message);
            return;
        }

        try {
            PortalInstallation admin = config.getAdminPortal();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("ENTITY_ID", ticket.getCrmItemId());
            fields.put("ENTITY_TYPE", "DYNAMIC_" + config.getEntityTypeId());
            fields.put("COMMENT", buildTimelineComment(message));

            JsonNode root = bitrixRestClient.callJson(
                    admin.getWebhookUrl(),
                    "crm.timeline.comment.add",
                    Map.of("fields", fields)
            );
            Long commentId = extractLong(root.path("result"));
            message.markCrmSynced(commentId);
            messageRepository.save(message);
        } catch (RuntimeException e) {
            String error = safeError(e, "Не удалось добавить сообщение в таймлайн");
            message.markCrmError(error);
            messageRepository.save(message);
            LOG.warn("CRM timeline sync failed: messageId={}, ticketId={}, error={}", message.getId(), ticket.getId(), error);
        }
    }

    @Transactional
    public void syncClosedStage(SupportTicket ticket) {
        if (ticket == null) {
            return;
        }
        syncTicket(ticket);
    }

    @Transactional
    public int retryPending() {
        // Пока CRM-интеграция не настроена, не переписываем одни и те же
        // NOT_CONFIGURED-записи каждую минуту. Как только конфигурация появится,
        // эти записи снова попадут в обычную очередь синхронизации.
        if (enabledConfig() == null) {
            return 0;
        }

        int processed = 0;
        List<SupportTicket> tickets = ticketRepository.findTop50ByCrmSyncStatusInOrderByIdAsc(RETRY_STATUSES);
        for (SupportTicket ticket : tickets) {
            syncTicket(ticket);
            processed++;
        }

        List<SupportMessage> messages = messageRepository
                .findTop100ByCrmSyncStatusInAndSupportTicketIsNotNullOrderByIdAsc(RETRY_STATUSES);
        for (SupportMessage message : messages) {
            syncMessage(message);
            processed++;
        }
        return processed;
    }

    private void createCrmItem(SupportTicket ticket,
                               CrmIntegrationConfig config,
                               PortalInstallation admin) {
        CompanyResolution company = resolveCompanyByPhone(admin, ticket.getClientInstallation().getClientPhone());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", ticket.getChatTitle());
        fields.put("categoryId", config.getCategoryId());
        fields.put("stageId", isLocallyClosed(ticket) ? config.getClosedStageId() : config.getOpenStageId());
        fields.put("assignedById", parseUserId(config.getResponsibleUserId()));
        if (company.companyId() != null) {
            fields.put("companyId", company.companyId());
        }

        JsonNode root = bitrixRestClient.callJson(
                admin.getWebhookUrl(),
                "crm.item.add",
                Map.of(
                        "entityTypeId", config.getEntityTypeId(),
                        "fields", fields
                )
        );

        JsonNode result = root.path("result");
        Long itemId = extractLong(result.path("item").path("id"));
        if (itemId == null) {
            itemId = extractLong(result.path("id"));
        }
        if (itemId == null) {
            throw new BitrixRestException("Bitrix24 не вернул ID созданного элемента смарт-процесса");
        }

        ticket.markCrmCreated(
                itemId,
                config.getEntityTypeId(),
                config.getCategoryId(),
                company.companyId(),
                company.status()
        );
        if (isLocallyClosed(ticket)) {
            ticket.markCrmClosed();
        }
        ticketRepository.save(ticket);
    }

    private CompanyResolution resolveCompanyByPhone(PortalInstallation admin, String phone) {
        if (phone == null || phone.isBlank()) {
            return new CompanyResolution(null, CrmCompanyMatchStatus.NOT_FOUND, "Телефон клиента не заполнен");
        }

        try {
            JsonNode root = bitrixRestClient.callJson(
                    admin.getWebhookUrl(),
                    "crm.duplicate.findbycomm",
                    Map.of(
                            "entity_type", "COMPANY",
                            "type", "PHONE",
                            "values", List.of(phone)
                    )
            );
            JsonNode companies = root.path("result").path("COMPANY");
            Set<Long> ids = new LinkedHashSet<>();
            if (companies.isArray()) {
                for (JsonNode node : companies) {
                    Long id = extractLong(node);
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }

            if (ids.size() == 1) {
                return new CompanyResolution(ids.iterator().next(), CrmCompanyMatchStatus.MATCHED, null);
            }
            if (ids.isEmpty()) {
                return new CompanyResolution(null, CrmCompanyMatchStatus.NOT_FOUND, "Компания по телефону не найдена");
            }
            return new CompanyResolution(null, CrmCompanyMatchStatus.MULTIPLE_FOUND,
                    "По телефону найдено несколько компаний: " + new ArrayList<>(ids));
        } catch (RuntimeException e) {
            return new CompanyResolution(null, CrmCompanyMatchStatus.ERROR,
                    "Поиск компании по телефону завершился ошибкой: " + safeError(e, "ошибка Bitrix24"));
        }
    }

    private void moveToClosedStage(SupportTicket ticket,
                                   CrmIntegrationConfig config,
                                   PortalInstallation admin) {
        bitrixRestClient.callJson(
                admin.getWebhookUrl(),
                "crm.item.update",
                Map.of(
                        "entityTypeId", config.getEntityTypeId(),
                        "id", ticket.getCrmItemId(),
                        "fields", Map.of("stageId", config.getClosedStageId())
                )
        );
        ticket.markCrmClosed();
        ticketRepository.save(ticket);
    }

    private CrmIntegrationConfig enabledConfig() {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN).orElse(null);
        if (admin == null) {
            return null;
        }
        return configRepository.findByAdminPortal_Id(admin.getId())
                .filter(CrmIntegrationConfig::isEnabled)
                .orElse(null);
    }

    private boolean isTechnicalSystemMessage(SupportMessage message) {
        return "SYSTEM_TO_CLIENT".equalsIgnoreCase(safe(message.getDirection()));
    }

    private String buildTimelineComment(SupportMessage message) {
        String role = switch (safe(message.getDirection())) {
            case "CLIENT_TO_ADMIN" -> "КЛИЕНТ";
            case "ADMIN_TO_CLIENT" -> "ТЕХПОДДЕРЖКА";
            case "SYSTEM_TO_CLIENT" -> "СИСТЕМА ТЕХПОДДЕРЖКИ";
            default -> "СООБЩЕНИЕ";
        };
        String sender = message.getSenderName() == null || message.getSenderName().isBlank()
                ? "Без имени"
                : message.getSenderName().trim();
        String time = message.getCreatedAt() == null ? "" : message.getCreatedAt().format(COMMENT_TIME);
        String text = message.getText() == null ? "" : message.getText().trim();
        return role + " · " + sender + (time.isBlank() ? "" : " · " + time) + "\n\n" + text;
    }

    private boolean isLocallyClosed(SupportTicket ticket) {
        return ticket.getStatus() == SupportTicketStatus.CLOSED
                || ticket.getStatus() == SupportTicketStatus.DELETING
                || ticket.getStatus() == SupportTicketStatus.DELETED;
    }

    private Integer parseUserId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException e) {
            throw new BitrixRestException("Некорректный ID ответственного сотрудника");
        }
    }

    private Long extractLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            return node.asLong();
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safeError(RuntimeException e, String fallback) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? fallback : message.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record CompanyResolution(
            Long companyId,
            CrmCompanyMatchStatus status,
            String warning
    ) {
    }
}
