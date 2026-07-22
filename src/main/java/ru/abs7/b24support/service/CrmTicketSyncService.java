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
            } else if (shouldRetryClientBinding(ticket)) {
                syncClientBinding(ticket, admin);
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

        if (ticket.getCrmItemId() == null || shouldRetryClientBinding(ticket)) {
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
        ClientResolution client = resolveClientByPhone(admin, ticket.getClientInstallation().getClientPhone());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", ticket.getChatTitle());
        fields.put("categoryId", config.getCategoryId());
        fields.put("stageId", isLocallyClosed(ticket) ? config.getClosedStageId() : config.getOpenStageId());
        fields.put("assignedById", parseUserId(config.getResponsibleUserId()));
        addClientFields(fields, client);

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
                client.companyId(),
                client.contactId(),
                client.status()
        );
        if (isLocallyClosed(ticket)) {
            ticket.markCrmClosed();
        }
        ticketRepository.save(ticket);
        logClientResolution(ticket, client, "CRM item created");
    }

    private void syncClientBinding(SupportTicket ticket, PortalInstallation admin) {
        ClientResolution client = resolveClientByPhone(admin, ticket.getClientInstallation().getClientPhone());
        Map<String, Object> fields = new LinkedHashMap<>();
        addClientFields(fields, client);

        if (!fields.isEmpty()) {
            bitrixRestClient.callJson(
                    admin.getWebhookUrl(),
                    "crm.item.update",
                    Map.of(
                            "entityTypeId", ticket.getCrmEntityTypeId(),
                            "id", ticket.getCrmItemId(),
                            "fields", fields
                    )
            );
        }

        ticket.markCrmClientResolved(client.companyId(), client.contactId(), client.status());
        ticketRepository.save(ticket);
        logClientResolution(ticket, client, fields.isEmpty() ? "CRM client not matched" : "CRM client binding updated");
    }

    private ClientResolution resolveClientByPhone(PortalInstallation admin, String phone) {
        if (phone == null || phone.isBlank()) {
            return new ClientResolution(null, null, CrmCompanyMatchStatus.NOT_FOUND,
                    "Телефон клиента не заполнен");
        }

        Set<Long> contactIds = new LinkedHashSet<>();
        Set<Long> companyIds = new LinkedHashSet<>();
        StringBuilder warning = new StringBuilder();
        int failedSearches = 0;

        try {
            contactIds.addAll(findIdsByPhone(admin, phone, "CONTACT"));
        } catch (RuntimeException e) {
            failedSearches++;
            appendWarning(warning, "поиск контакта завершился ошибкой: " + safeError(e, "ошибка Bitrix24"));
        }

        try {
            companyIds.addAll(findIdsByPhone(admin, phone, "COMPANY"));
        } catch (RuntimeException e) {
            failedSearches++;
            appendWarning(warning, "поиск компании завершился ошибкой: " + safeError(e, "ошибка Bitrix24"));
        }

        if (failedSearches == 2) {
            return new ClientResolution(null, null, CrmCompanyMatchStatus.ERROR, warning.toString());
        }

        Long contactId = contactIds.size() == 1 ? contactIds.iterator().next() : null;
        Long companyId = companyIds.size() == 1 ? companyIds.iterator().next() : null;

        if (contactIds.size() > 1) {
            appendWarning(warning, "по телефону найдено несколько контактов: " + new ArrayList<>(contactIds));
        }
        if (companyIds.size() > 1) {
            appendWarning(warning, "по телефону найдено несколько компаний: " + new ArrayList<>(companyIds));
        }

        if (contactId != null) {
            try {
                ContactCompanies linked = resolveContactCompanies(admin, contactId);
                if (linked.primaryCompanyId() != null) {
                    companyId = linked.primaryCompanyId();
                } else if (companyId == null && linked.companyIds().size() == 1) {
                    companyId = linked.companyIds().iterator().next();
                } else if (linked.companyIds().size() > 1) {
                    appendWarning(warning,
                            "контакт " + contactId + " связан с несколькими компаниями: "
                                    + new ArrayList<>(linked.companyIds()));
                }
            } catch (RuntimeException e) {
                appendWarning(warning,
                        "контакт найден, но его компании получить не удалось: "
                                + safeError(e, "ошибка Bitrix24"));
            }
        }

        if (contactId != null || companyId != null) {
            return new ClientResolution(companyId, contactId, CrmCompanyMatchStatus.MATCHED,
                    warning.isEmpty() ? null : warning.toString());
        }
        if (!contactIds.isEmpty() || !companyIds.isEmpty()) {
            return new ClientResolution(null, null, CrmCompanyMatchStatus.MULTIPLE_FOUND,
                    warning.isEmpty() ? "По телефону найдено несколько CRM-клиентов" : warning.toString());
        }
        return new ClientResolution(null, null, CrmCompanyMatchStatus.NOT_FOUND,
                warning.isEmpty() ? "По телефону не найдено ни контакта, ни компании" : warning.toString());
    }

    private Set<Long> findIdsByPhone(PortalInstallation admin, String phone, String entityType) {
        JsonNode root = bitrixRestClient.callJson(
                admin.getWebhookUrl(),
                "crm.duplicate.findbycomm",
                Map.of(
                        "entity_type", entityType,
                        "type", "PHONE",
                        "values", List.of(phone)
                )
        );
        JsonNode result = root.path("result");
        if (result.isArray()) {
            // Некоторые порталы возвращают пустой результат как [],
            // несмотря на документированный объект {"CONTACT": [...]}.
            return extractIds(result);
        }
        return extractIds(result.path(entityType));
    }

    private ContactCompanies resolveContactCompanies(PortalInstallation admin, Long contactId) {
        JsonNode root = bitrixRestClient.callJson(
                admin.getWebhookUrl(),
                "crm.contact.company.items.get",
                Map.of("id", contactId)
        );

        Set<Long> companyIds = new LinkedHashSet<>();
        Long primaryCompanyId = null;
        JsonNode result = root.path("result");
        if (result.isArray()) {
            for (JsonNode binding : result) {
                Long companyId = extractLong(binding.path("COMPANY_ID"));
                if (companyId == null) {
                    companyId = extractLong(binding.path("companyId"));
                }
                if (companyId == null) {
                    continue;
                }
                companyIds.add(companyId);
                String primary = binding.path("IS_PRIMARY").asText(
                        binding.path("isPrimary").asText("N")
                );
                if ("Y".equalsIgnoreCase(primary)) {
                    primaryCompanyId = companyId;
                }
            }
        }
        return new ContactCompanies(companyIds, primaryCompanyId);
    }

    private void addClientFields(Map<String, Object> fields, ClientResolution client) {
        if (client.companyId() != null) {
            fields.put("companyId", client.companyId());
        }
        if (client.contactId() != null) {
            fields.put("contactId", client.contactId());
            fields.put("contactIds", List.of(client.contactId()));
        }
    }

    private Set<Long> extractIds(JsonNode nodes) {
        Set<Long> ids = new LinkedHashSet<>();
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                Long id = extractLong(node);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private void appendWarning(StringBuilder warning, String value) {
        if (!warning.isEmpty()) {
            warning.append("; ");
        }
        warning.append(value);
    }

    private boolean shouldRetryClientBinding(SupportTicket ticket) {
        return ticket.getCrmItemId() != null
                && ticket.getCrmCompanyId() == null
                && ticket.getCrmContactId() == null
                && (ticket.getCrmSyncStatus() != CrmSyncStatus.SYNCED
                    || ticket.getCrmCompanyMatchStatus() == CrmCompanyMatchStatus.NOT_FOUND);
    }

    private void logClientResolution(SupportTicket ticket, ClientResolution client, String action) {
        LOG.info("{}: ticketId={}, crmItemId={}, companyId={}, contactId={}, status={}, warning={}",
                action, ticket.getId(), ticket.getCrmItemId(), client.companyId(), client.contactId(),
                client.status(), client.warning());
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

    private record ClientResolution(
            Long companyId,
            Long contactId,
            CrmCompanyMatchStatus status,
            String warning
    ) {
    }

    private record ContactCompanies(
            Set<Long> companyIds,
            Long primaryCompanyId
    ) {
    }
}
