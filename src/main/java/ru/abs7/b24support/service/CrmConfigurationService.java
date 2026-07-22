package ru.abs7.b24support.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.crm.CrmCategoryOption;
import ru.abs7.b24support.api.dto.crm.CrmIntegrationConfigRequest;
import ru.abs7.b24support.api.dto.crm.CrmIntegrationConfigResponse;
import ru.abs7.b24support.api.dto.crm.CrmSmartProcessOption;
import ru.abs7.b24support.api.dto.crm.CrmStageOption;
import ru.abs7.b24support.api.dto.crm.CrmValidationResponse;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.BitrixUser;
import ru.abs7.b24support.domain.CrmIntegrationConfig;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.repo.BitrixUserRepository;
import ru.abs7.b24support.repo.CrmIntegrationConfigRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CrmConfigurationService {

    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 20;

    private final PortalInstallationRepository portalRepository;
    private final BitrixUserRepository bitrixUserRepository;
    private final CrmIntegrationConfigRepository configRepository;
    private final BitrixRestClient bitrixRestClient;

    public CrmConfigurationService(PortalInstallationRepository portalRepository,
                                   BitrixUserRepository bitrixUserRepository,
                                   CrmIntegrationConfigRepository configRepository,
                                   BitrixRestClient bitrixRestClient) {
        this.portalRepository = portalRepository;
        this.bitrixUserRepository = bitrixUserRepository;
        this.configRepository = configRepository;
        this.bitrixRestClient = bitrixRestClient;
    }

    @Transactional(readOnly = true)
    public CrmIntegrationConfigResponse getConfig(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        return configRepository.findByAdminPortal_Id(admin.getId())
                .map(CrmIntegrationConfigResponse::from)
                .orElseGet(CrmIntegrationConfigResponse::empty);
    }

    public List<CrmSmartProcessOption> listProcesses(Long portalId) {
        PortalInstallation admin = findAdminPortalWithWebhook(portalId);
        List<CrmSmartProcessOption> result = new ArrayList<>();

        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("order", Map.of("title", "ASC"));
            payload.put("start", start);

            JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "crm.type.list", payload);
            JsonNode types = root.path("result").path("types");
            if (!types.isArray()) {
                throw new BitrixRestException("Bitrix24 вернул неожиданный ответ crm.type.list");
            }

            for (JsonNode type : types) {
                Integer entityTypeId = integer(type, "entityTypeId", "ENTITY_TYPE_ID");
                if (entityTypeId == null) {
                    continue;
                }
                String title = text(type, "title", "TITLE");
                boolean stagesEnabled = yes(type, "isStagesEnabled", "IS_STAGES_ENABLED");
                boolean categoriesEnabled = yes(type, "isCategoriesEnabled", "IS_CATEGORIES_ENABLED");
                boolean clientEnabled = yes(type, "isClientEnabled", "IS_CLIENT_ENABLED");
                boolean eligible = stagesEnabled && clientEnabled;
                String reason = null;
                if (!stagesEnabled) {
                    reason = "В смарт-процессе отключены стадии";
                } else if (!clientEnabled) {
                    reason = "В смарт-процессе отключена привязка к клиентам";
                }
                result.add(new CrmSmartProcessOption(
                        entityTypeId,
                        title == null ? "Смарт-процесс " + entityTypeId : title,
                        stagesEnabled,
                        categoriesEnabled,
                        clientEnabled,
                        eligible,
                        reason
                ));
            }

            if (types.size() < PAGE_SIZE) {
                break;
            }
            start += PAGE_SIZE;
        }

        return result.stream()
                .sorted(Comparator.comparing(CrmSmartProcessOption::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<CrmCategoryOption> listCategories(Long portalId, Integer entityTypeId) {
        PortalInstallation admin = findAdminPortalWithWebhook(portalId);
        requirePositive(entityTypeId, "Некорректный ID смарт-процесса");

        CrmSmartProcessOption process = listProcesses(portalId).stream()
                .filter(item -> Objects.equals(item.entityTypeId(), entityTypeId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Смарт-процесс не найден"));
        if (!process.categoriesEnabled()) {
            return List.of(new CrmCategoryOption(0, "Общая воронка", true));
        }

        List<CrmCategoryOption> result = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode root = bitrixRestClient.callJson(
                    admin.getWebhookUrl(),
                    "crm.category.list",
                    Map.of("entityTypeId", entityTypeId, "start", start)
            );
            JsonNode categories = root.path("result").path("categories");
            if (!categories.isArray()) {
                throw new BitrixRestException("Bitrix24 вернул неожиданный ответ crm.category.list");
            }

            for (JsonNode category : categories) {
                Integer id = integer(category, "id", "ID");
                if (id == null) {
                    continue;
                }
                String name = text(category, "name", "NAME");
                boolean defaultCategory = yes(category, "isDefault", "IS_DEFAULT");
                result.add(new CrmCategoryOption(id, name == null ? "Воронка " + id : name, defaultCategory));
            }
            if (categories.size() < PAGE_SIZE) {
                break;
            }
            start += PAGE_SIZE;
        }

        if (result.isEmpty()) {
            result.add(new CrmCategoryOption(0, "Общая воронка", true));
        }
        return result.stream()
                .sorted(Comparator.comparing(CrmCategoryOption::defaultCategory).reversed()
                        .thenComparing(CrmCategoryOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<CrmStageOption> listStages(Long portalId, Integer entityTypeId, Integer categoryId) {
        PortalInstallation admin = findAdminPortalWithWebhook(portalId);
        requirePositive(entityTypeId, "Некорректный ID смарт-процесса");
        if (categoryId == null || categoryId < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный ID воронки");
        }

        String statusEntityId = "DYNAMIC_" + entityTypeId + "_STAGE_" + categoryId;
        JsonNode root = bitrixRestClient.callJson(
                admin.getWebhookUrl(),
                "crm.status.list",
                Map.of(
                        "filter", Map.of("ENTITY_ID", statusEntityId),
                        "order", Map.of("SORT", "ASC")
                )
        );

        JsonNode statuses = root.path("result");
        if (statuses.isObject() && statuses.has("statuses")) {
            statuses = statuses.path("statuses");
        }
        if (!statuses.isArray()) {
            throw new BitrixRestException("Bitrix24 вернул неожиданный ответ crm.status.list");
        }

        List<CrmStageOption> result = new ArrayList<>();
        for (JsonNode stage : statuses) {
            String id = text(stage, "STATUS_ID", "statusId", "id", "ID");
            if (id == null) {
                continue;
            }
            String name = text(stage, "NAME", "name");
            Integer sort = integer(stage, "SORT", "sort");
            String semantics = text(stage, "SEMANTICS", "semantics");
            result.add(new CrmStageOption(
                    id,
                    name == null ? id : name,
                    sort == null ? 0 : sort,
                    normalizeSemantics(semantics)
            ));
        }
        return result.stream().sorted(Comparator.comparing(CrmStageOption::sort)).toList();
    }

    @Transactional
    public CrmIntegrationConfigResponse saveConfig(Long portalId, CrmIntegrationConfigRequest request) {
        PortalInstallation admin = findAdminPortalWithWebhook(portalId);

        CrmSmartProcessOption process = listProcesses(portalId).stream()
                .filter(item -> Objects.equals(item.entityTypeId(), request.entityTypeId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный смарт-процесс не найден"));
        if (!process.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, process.unavailableReason());
        }

        CrmCategoryOption category = listCategories(portalId, request.entityTypeId()).stream()
                .filter(item -> Objects.equals(item.id(), request.categoryId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранная воронка не найдена"));

        List<CrmStageOption> stages = listStages(portalId, request.entityTypeId(), request.categoryId());
        CrmStageOption openStage = findStage(stages, request.openStageId(), "Стадия открытого обращения не найдена");
        CrmStageOption closedStage = findStage(stages, request.closedStageId(), "Стадия завершённого обращения не найдена");
        if (Objects.equals(openStage.id(), closedStage.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для работы и завершения выбери разные стадии");
        }

        BitrixUser responsible = bitrixUserRepository
                .findByPortalInstallationIdAndBitrixUserId(admin.getId(), request.responsibleUserId().trim())
                .filter(BitrixUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный ответственный не найден среди активных сотрудников"));

        CrmIntegrationConfig config = configRepository.findByAdminPortal_Id(admin.getId())
                .orElseGet(() -> new CrmIntegrationConfig(admin));
        config.configure(
                process.entityTypeId(),
                process.title(),
                category.id(),
                category.name(),
                openStage.id(),
                openStage.name(),
                closedStage.id(),
                closedStage.name(),
                responsible.getBitrixUserId(),
                responsible.getDisplayName() == null ? "Пользователь " + responsible.getBitrixUserId() : responsible.getDisplayName()
        );
        return CrmIntegrationConfigResponse.from(configRepository.save(config));
    }

    @Transactional
    public CrmValidationResponse validate(Long portalId) {
        PortalInstallation admin = findAdminPortalWithWebhook(portalId);
        CrmIntegrationConfig config = configRepository.findByAdminPortal_Id(admin.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Интеграция со смарт-процессом ещё не настроена"));

        try {
            CrmSmartProcessOption process = listProcesses(portalId).stream()
                    .filter(item -> Objects.equals(item.entityTypeId(), config.getEntityTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new BitrixRestException("Смарт-процесс больше не доступен"));
            if (!process.eligible()) {
                throw new BitrixRestException(process.unavailableReason());
            }
            boolean categoryExists = listCategories(portalId, config.getEntityTypeId()).stream()
                    .anyMatch(item -> Objects.equals(item.id(), config.getCategoryId()));
            if (!categoryExists) {
                throw new BitrixRestException("Настроенная воронка больше не доступна");
            }
            List<CrmStageOption> stages = listStages(portalId, config.getEntityTypeId(), config.getCategoryId());
            findStage(stages, config.getOpenStageId(), "Стадия открытого обращения больше не доступна");
            findStage(stages, config.getClosedStageId(), "Стадия завершения больше не доступна");
            bitrixUserRepository.findByPortalInstallationIdAndBitrixUserId(admin.getId(), config.getResponsibleUserId())
                    .filter(BitrixUser::isActive)
                    .orElseThrow(() -> new BitrixRestException("Ответственный сотрудник больше не доступен"));

            config.markValid();
            configRepository.save(config);
            return new CrmValidationResponse(true, "Интеграция со смарт-процессом проверена", CrmIntegrationConfigResponse.from(config));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Не удалось проверить CRM-интеграцию" : e.getMessage();
            config.markInvalid(message);
            configRepository.save(config);
            return new CrmValidationResponse(false, message, CrmIntegrationConfigResponse.from(config));
        }
    }

    @Transactional(readOnly = true)
    public CrmIntegrationConfig findEnabledConfig() {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)
                .orElse(null);
        if (admin == null) {
            return null;
        }
        return configRepository.findByAdminPortal_Id(admin.getId())
                .filter(CrmIntegrationConfig::isEnabled)
                .orElse(null);
    }

    private CrmStageOption findStage(List<CrmStageOption> stages, String id, String error) {
        String cleanId = id == null ? "" : id.trim();
        return stages.stream()
                .filter(item -> item.id().equals(cleanId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, error));
    }

    private PortalInstallation findAdminPortal(Long portalId) {
        PortalInstallation portal = portalRepository.findById(portalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Админский портал не найден"));
        if (portal.getRole() != PortalRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRM-интеграция настраивается только для админского портала");
        }
        return portal;
    }

    private PortalInstallation findAdminPortalWithWebhook(Long portalId) {
        PortalInstallation portal = findAdminPortal(portalId);
        if (portal.getWebhookUrl() == null || portal.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У админского портала не заполнен Webhook / REST URL");
        }
        return portal;
    }

    private void requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean yes(JsonNode node, String... fields) {
        String value = text(node, fields);
        return "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String result = value.asText(null);
                if (result != null && !result.isBlank()) {
                    return result.trim();
                }
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.canConvertToInt()) {
                return value.asInt();
            }
            String text = value.asText(null);
            if (text != null) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private String normalizeSemantics(String value) {
        if (value == null || value.isBlank()) {
            return "PROCESS";
        }
        return switch (value.trim().toUpperCase()) {
            case "S", "SUCCESS" -> "SUCCESS";
            case "F", "FAILURE" -> "FAILURE";
            default -> "PROCESS";
        };
    }
}
