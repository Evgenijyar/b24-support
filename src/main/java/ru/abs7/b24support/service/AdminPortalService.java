package ru.abs7.b24support.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.PortalInstallationResponse;
import ru.abs7.b24support.api.dto.admin.AdminPortalActionResponse;
import ru.abs7.b24support.api.dto.admin.AdminPortalSummaryResponse;
import ru.abs7.b24support.api.dto.admin.BitrixUserListResponse;
import ru.abs7.b24support.api.dto.admin.BitrixUserResponse;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.BitrixUser;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.PortalStatus;
import ru.abs7.b24support.repo.BitrixUserRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminPortalService {

    private static final int MAX_USER_GET_PAGES = 50;
    private static final String ADMIN_BOT_CODE = "smart_sales_support_admin_bot";
    private static final String ADMIN_BOT_TYPE = "supervisor";
    private static final String ADMIN_CHAT_TITLE = "Техподдержка админ";

    private final PortalInstallationRepository portalRepository;
    private final BitrixUserRepository bitrixUserRepository;
    private final BitrixRestClient bitrixRestClient;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminPortalService(PortalInstallationRepository portalRepository,
                              BitrixUserRepository bitrixUserRepository,
                              BitrixRestClient bitrixRestClient,
                              ObjectMapper objectMapper,
                              @Value("${app.public-base-url}") String publicBaseUrl) {
        this.portalRepository = portalRepository;
        this.bitrixUserRepository = bitrixUserRepository;
        this.bitrixRestClient = bitrixRestClient;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public AdminPortalSummaryResponse summary() {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN).orElse(null);
        if (admin == null) {
            return new AdminPortalSummaryResponse(null, 0, 0);
        }

        return new AdminPortalSummaryResponse(
                PortalInstallationResponse.from(admin),
                bitrixUserRepository.countByPortalInstallationId(admin.getId()),
                bitrixUserRepository.countByPortalInstallationIdAndSupportMemberTrue(admin.getId())
        );
    }

    @Transactional(readOnly = true)
    public BitrixUserListResponse users(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        List<BitrixUserResponse> users = bitrixUserRepository
                .findAllByPortalInstallationIdOrderBySupportMemberDescLastNameAscFirstNameAscIdAsc(admin.getId())
                .stream()
                .map(BitrixUserResponse::from)
                .toList();

        return new BitrixUserListResponse(
                admin.getId(),
                users.size(),
                users.stream().filter(BitrixUserResponse::supportMember).count(),
                users
        );
    }

    @Transactional
    public AdminPortalActionResponse testConnection(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("start", "0");
            bitrixRestClient.call(admin.getWebhookUrl(), "user.get", params);

            markSuccess(admin);
            return response(true, "Подключение к Bitrix24 работает", admin);
        } catch (BitrixRestException e) {
            markError(admin, e);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public AdminPortalActionResponse loadUsers(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);

        try {
            List<JsonNode> remoteUsers = fetchAllUsers(admin.getWebhookUrl());
            for (JsonNode remoteUser : remoteUsers) {
                upsertUser(admin, remoteUser);
            }

            markSuccess(admin);
            return response(true, "Сотрудники загружены: " + remoteUsers.size(), admin);
        } catch (BitrixRestException e) {
            markError(admin, e);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public BitrixUserListResponse saveSupportUsers(Long portalId, List<Long> userIds) {
        PortalInstallation admin = findAdminPortal(portalId);
        Set<Long> selectedIds = (userIds == null ? List.<Long>of() : userIds)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<BitrixUser> users = bitrixUserRepository
                .findAllByPortalInstallationIdOrderBySupportMemberDescLastNameAscFirstNameAscIdAsc(admin.getId());

        OffsetDateTime now = OffsetDateTime.now();
        for (BitrixUser user : users) {
            user.setSupportMember(selectedIds.contains(user.getId()));
            user.setUpdatedAt(now);
        }
        bitrixUserRepository.saveAll(users);

        return users(admin.getId());
    }

    @Transactional
    public AdminPortalActionResponse registerBot(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);

        try {
            String botToken = valueOrDefault(admin.getBotToken(), generateBotToken());
            String webhookUrl = buildAdminEventWebhookUrl();

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("code", ADMIN_BOT_CODE);
            fields.put("botToken", botToken);
            fields.put("type", ADMIN_BOT_TYPE);
            fields.put("eventMode", "webhook");
            fields.put("webhookUrl", webhookUrl);
            fields.put("properties", Map.of(
                    "name", "Техподдержка «Умные продажи»",
                    "workPosition", "Маршрутизатор обращений клиентов"
            ));

            JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Bot.register", Map.of("fields", fields));
            JsonNode bot = root.path("result").path("bot");
            String botId = text(bot, "id");
            if (botId == null || botId.isBlank()) {
                throw new BitrixRestException("Bitrix24 не вернул ID зарегистрированного бота");
            }

            admin.setBotId(botId);
            admin.setBotCode(valueOrDefault(text(bot, "code"), ADMIN_BOT_CODE));
            admin.setBotType(valueOrDefault(text(bot, "type"), ADMIN_BOT_TYPE));
            admin.setBotToken(botToken);
            admin.setBotEventWebhookUrl(webhookUrl);
            admin.setBotRegisteredAt(OffsetDateTime.now());

            updateAdminBotWebhook(admin, webhookUrl);
            markSuccess(admin);

            return response(true, "Админский бот создан / обновлён: ID " + botId, admin);
        } catch (BitrixRestException e) {
            markError(admin, e);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public AdminPortalActionResponse createSupportChat(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);
        requireBot(admin);

        List<Integer> supportUserIds = supportBitrixUserIds(admin);
        if (supportUserIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала выбери операторов поддержки");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("botId", parseInteger(admin.getBotId(), "Bot ID"));
            payload.put("botToken", admin.getBotToken());
            payload.put("fields", Map.of(
                    "title", ADMIN_CHAT_TITLE,
                    "description", "Общий чат операторов поддержки для обращений из клиентских Bitrix24",
                    "color", "mint",
                    "userIds", supportUserIds,
                    "message", "Чат технической поддержки создан. Следующим шагом сюда будут приходить обращения клиентов."
            ));

            JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.add", payload);
            JsonNode chat = root.path("result").path("chat");
            String chatId = text(chat, "id");
            String dialogId = text(chat, "dialogId");
            if (dialogId == null || dialogId.isBlank()) {
                throw new BitrixRestException("Bitrix24 не вернул dialogId созданного чата");
            }

            admin.setSupportChatId(chatId);
            admin.setSupportDialogId(dialogId);
            admin.setSupportChatCreatedAt(OffsetDateTime.now());
            markSuccess(admin);

            return response(true, "Админский чат создан: " + dialogId, admin);
        } catch (BitrixRestException e) {
            markError(admin, e);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public AdminPortalActionResponse addSupportUsersToChat(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);
        requireBot(admin);
        requireSupportDialog(admin);

        List<Integer> supportUserIds = supportBitrixUserIds(admin);
        if (supportUserIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала выбери операторов поддержки");
        }

        try {
            Map<String, Object> payload = Map.of(
                    "botId", parseInteger(admin.getBotId(), "Bot ID"),
                    "botToken", admin.getBotToken(),
                    "dialogId", admin.getSupportDialogId(),
                    "userIds", supportUserIds
            );

            bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.User.add", payload);
            markSuccess(admin);
            return response(true, "Операторы добавлены в админский чат: " + supportUserIds.size(), admin);
        } catch (BitrixRestException e) {
            markError(admin, e);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public AdminPortalActionResponse sendTestMessage(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);
        requireBot(admin);
        requireSupportDialog(admin);

        try {
            Map<String, Object> payload = Map.of(
                    "botId", parseInteger(admin.getBotId(), "Bot ID"),
                    "botToken", admin.getBotToken(),
                    "dialogId", admin.getSupportDialogId(),
                    "fields", Map.of(
                            "message", "Тестовое сообщение от B24 Support. Админский чат подключён корректно.",
                            "urlPreview", false
                    )
            );

            JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.Message.send", payload);
            String messageId = text(root.path("result"), "id");
            markSuccess(admin);
            return response(true, "Тестовое сообщение отправлено" + (messageId == null ? "" : ": " + messageId), admin);
        } catch (BitrixRestException e) {
            markError(admin, e);
            return response(false, e.getMessage(), admin);
        }
    }

    private void updateAdminBotWebhook(PortalInstallation admin, String webhookUrl) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventMode", "webhook");
        fields.put("webhookUrl", webhookUrl);
        fields.put("type", ADMIN_BOT_TYPE);
        fields.put("properties", Map.of(
                "name", "Техподдержка «Умные продажи»",
                "workPosition", "Маршрутизатор обращений клиентов"
        ));

        Map<String, Object> payload = Map.of(
                "botId", parseInteger(admin.getBotId(), "Bot ID"),
                "botToken", admin.getBotToken(),
                "fields", fields
        );
        bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Bot.update", payload);
    }

    private String buildAdminEventWebhookUrl() {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "app.public-base-url не настроен");
        }
        return base + "/api/bitrix/events/admin";
    }

    private List<JsonNode> fetchAllUsers(String webhookUrl) {
        List<JsonNode> users = new ArrayList<>();
        int start = 0;

        for (int page = 0; page < MAX_USER_GET_PAGES; page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("start", String.valueOf(start));
            params.put("ORDER[LAST_NAME]", "ASC");
            params.put("ORDER[NAME]", "ASC");

            JsonNode root = bitrixRestClient.call(webhookUrl, "user.get", params);
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                throw new BitrixRestException("Bitrix24 вернул неожиданный формат user.get");
            }

            result.forEach(users::add);

            if (root.has("next") && root.path("next").canConvertToInt()) {
                start = root.path("next").asInt();
            } else {
                break;
            }
        }

        return users;
    }

    private void upsertUser(PortalInstallation admin, JsonNode remoteUser) {
        String bitrixUserId = text(remoteUser, "ID");
        if (bitrixUserId == null || bitrixUserId.isBlank()) {
            return;
        }

        BitrixUser user = bitrixUserRepository
                .findByPortalInstallationIdAndBitrixUserId(admin.getId(), bitrixUserId)
                .orElseGet(() -> new BitrixUser(admin, bitrixUserId));

        user.setActive(!"N".equalsIgnoreCase(text(remoteUser, "ACTIVE")));
        user.setFirstName(text(remoteUser, "NAME"));
        user.setLastName(text(remoteUser, "LAST_NAME"));
        user.setSecondName(text(remoteUser, "SECOND_NAME"));
        user.setEmail(text(remoteUser, "EMAIL"));
        user.setWorkPosition(text(remoteUser, "WORK_POSITION"));
        user.setPersonalPhoto(text(remoteUser, "PERSONAL_PHOTO"));
        user.setDisplayName(buildDisplayName(user));
        user.setRawJson(toJson(remoteUser));
        user.markLoaded();

        bitrixUserRepository.save(user);
    }

    private List<Integer> supportBitrixUserIds(PortalInstallation admin) {
        return bitrixUserRepository
                .findAllByPortalInstallationIdAndSupportMemberTrueOrderByLastNameAscFirstNameAscIdAsc(admin.getId())
                .stream()
                .map(BitrixUser::getBitrixUserId)
                .map(this::tryParseInteger)
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildDisplayName(BitrixUser user) {
        String joined = List.of(
                        valueOrEmpty(user.getLastName()),
                        valueOrEmpty(user.getFirstName()),
                        valueOrEmpty(user.getSecondName())
                )
                .stream()
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));

        if (!joined.isBlank()) {
            return joined;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "Пользователь " + user.getBitrixUserId();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private String generateBotToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Integer tryParseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer parseInteger(String value, String fieldName) {
        Integer parsed = tryParseInteger(value);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " не заполнен или некорректен");
        }
        return parsed;
    }

    private void requireWebhook(PortalInstallation admin) {
        if (admin.getWebhookUrl() == null || admin.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У админского портала не заполнен Webhook / REST URL");
        }
    }

    private void requireBot(PortalInstallation admin) {
        if (admin.getBotId() == null || admin.getBotId().isBlank() || admin.getBotToken() == null || admin.getBotToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала создай / проверь бота");
        }
    }

    private void requireSupportDialog(PortalInstallation admin) {
        if (admin.getSupportDialogId() == null || admin.getSupportDialogId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала создай админский чат");
        }
    }

    private PortalInstallation findAdminPortal(Long portalId) {
        PortalInstallation portal = portalRepository.findById(portalId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Портал не найден"
        ));

        if (portal.getRole() != PortalRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Операция доступна только для админского портала");
        }

        return portal;
    }

    private void markSuccess(PortalInstallation admin) {
        admin.setStatus(PortalStatus.ACTIVE);
        admin.setConnectedAt(OffsetDateTime.now());
        admin.setLastError(null);
        admin.markUpdated();
        portalRepository.save(admin);
    }

    private void markError(PortalInstallation admin, BitrixRestException e) {
        admin.setStatus(PortalStatus.ERROR);
        admin.setLastError(e.getMessage());
        admin.markUpdated();
        portalRepository.save(admin);
    }

    private AdminPortalActionResponse response(boolean success, String message, PortalInstallation admin) {
        return new AdminPortalActionResponse(
                success,
                message,
                PortalInstallationResponse.from(admin),
                bitrixUserRepository.countByPortalInstallationId(admin.getId()),
                bitrixUserRepository.countByPortalInstallationIdAndSupportMemberTrue(admin.getId())
        );
    }
}
