package ru.abs7.b24support.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.PortalInstallationResponse;
import ru.abs7.b24support.api.dto.client.ClientPortalActionResponse;
import ru.abs7.b24support.api.dto.client.SupportMessageListResponse;
import ru.abs7.b24support.api.dto.client.SupportMessageResponse;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.PortalStatus;
import ru.abs7.b24support.domain.SupportMessage;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import ru.abs7.b24support.repo.SupportMessageRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ClientPortalService {

    private static final String CLIENT_BOT_CODE = "smart_sales_support_client_bot";
    private static final String CLIENT_BOT_TYPE = "bot";
    private static final String CLIENT_BOT_NAME = "Техподдержка «Умные продажи»";
    private static final Pattern CLIENT_CODE_COMMAND = Pattern.compile("^\\s*#?(c_[A-Za-z0-9_-]+)\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final PortalInstallationRepository portalRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final BitrixRestClient bitrixRestClient;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientPortalService(PortalInstallationRepository portalRepository,
                               SupportMessageRepository supportMessageRepository,
                               BitrixRestClient bitrixRestClient,
                               ObjectMapper objectMapper,
                               @Value("${app.public-base-url}") String publicBaseUrl) {
        this.portalRepository = portalRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.bitrixRestClient = bitrixRestClient;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public SupportMessageListResponse recentMessages() {
        List<SupportMessageResponse> items = supportMessageRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(SupportMessageResponse::from)
                .toList();
        return new SupportMessageListResponse(items.size(), items);
    }

    @Transactional
    public ClientPortalActionResponse testConnection(Long portalId) {
        PortalInstallation client = findClientPortal(portalId);
        requireWebhook(client);

        try {
            bitrixRestClient.call(client.getWebhookUrl(), "user.get", Map.of("start", "0"));
            markSuccess(client);
            return response(true, "Клиентский Bitrix24 подключён", client);
        } catch (BitrixRestException e) {
            markError(client, e.getMessage());
            return response(false, e.getMessage(), client);
        }
    }

    @Transactional
    public ClientPortalActionResponse registerClientBot(Long portalId) {
        PortalInstallation client = findClientPortal(portalId);
        requireWebhook(client);

        try {
            String botToken = valueOrDefault(client.getBotToken(), generateBotToken());
            String webhookUrl = buildClientEventWebhookUrl(client);

            if (client.getBotId() != null && !client.getBotId().isBlank()) {
                client.setBotToken(botToken);
                configureExistingClientBotWebhook(client, webhookUrl);
                markSuccess(client);
                return response(true, "Клиентский бот уже есть, webhook-маршрутизация обновлена: ID " + client.getBotId(), client);
            }

            Map<String, Object> properties = Map.of(
                    "name", CLIENT_BOT_NAME,
                    "workPosition", "Помощник технической поддержки"
            );

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("code", CLIENT_BOT_CODE);
            fields.put("botToken", botToken);
            fields.put("type", CLIENT_BOT_TYPE);
            fields.put("eventMode", "webhook");
            fields.put("webhookUrl", webhookUrl);
            fields.put("isHidden", false);
            fields.put("properties", properties);

            JsonNode root = bitrixRestClient.callJson(client.getWebhookUrl(), "imbot.v2.Bot.register", Map.of("fields", fields));
            JsonNode bot = root.path("result").path("bot");
            String botId = text(bot, "id");
            if (botId == null || botId.isBlank()) {
                throw new BitrixRestException("Bitrix24 не вернул ID клиентского бота");
            }

            client.setBotId(botId);
            client.setBotCode(valueOrDefault(text(bot, "code"), CLIENT_BOT_CODE));
            client.setBotType(valueOrDefault(text(bot, "type"), CLIENT_BOT_TYPE));
            client.setBotToken(botToken);
            configureExistingClientBotWebhook(client, webhookUrl);
            markSuccess(client);

            return response(true, "Клиентский бот создан, webhook-маршрутизация включена: ID " + botId, client);
        } catch (BitrixRestException e) {
            markError(client, e.getMessage());
            return response(false, e.getMessage(), client);
        }
    }

    @Transactional
    public ClientPortalActionResponse repairRouting(Long portalId) {
        PortalInstallation client = findClientPortal(portalId);
        requireWebhook(client);
        requireClientBot(client);

        try {
            String webhookUrl = buildClientEventWebhookUrl(client);
            configureExistingClientBotWebhook(client, webhookUrl);
            markSuccess(client);
            return response(true, "Маршрутизация клиентского бота проверена и зафиксирована: " + webhookUrl, client);
        } catch (BitrixRestException e) {
            markError(client, e.getMessage());
            return response(false, e.getMessage(), client);
        }
    }

    @Transactional
    public Map<String, Object> handleClientWebhook(String clientCode, Map<String, String> payload) {
        PortalInstallation client = portalRepository.findByClientCode(clientCode)
                .filter(portal -> portal.getRole() == PortalRole.CLIENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Клиентский портал не найден"));

        OffsetDateTime now = OffsetDateTime.now();
        client.setLastEventAt(now);
        client.markUpdated();

        String event = value(payload, "event");
        if (!"ONIMBOTV2MESSAGEADD".equalsIgnoreCase(event)) {
            portalRepository.save(client);
            return Map.of("ok", true, "processed", false, "event", valueOrDefault(event, "unknown"));
        }

        String botId = firstValue(payload, "data[bot][id]", "bot[id]", "data.bot.id");
        if (client.getBotId() != null && botId != null && !Objects.equals(client.getBotId(), botId)) {
            portalRepository.save(client);
            return Map.of("ok", true, "processed", false, "reason", "bot_id_mismatch");
        }

        String userIsBot = firstValue(payload, "data[user][bot]", "user[bot]", "data.user.bot");
        if (isTruthy(userIsBot)) {
            portalRepository.save(client);
            return Map.of("ok", true, "processed", false, "reason", "bot_author");
        }

        String text = firstValue(payload, "data[message][text]", "message[text]", "data.message.text");
        String messageId = firstValue(payload, "data[message][id]", "message[id]", "data.message.id");
        String dialogId = firstValue(payload, "data[chat][dialogId]", "chat[dialogId]", "data.chat.dialogId");
        String chatId = firstValue(payload, "data[message][chatId]", "message[chatId]", "data.message.chatId", "data[chat][id]", "chat[id]", "data.chat.id");
        String userId = firstValue(payload, "data[user][id]", "user[id]", "data.user.id", "data[message][authorId]", "message[authorId]", "data.message.authorId");
        String userName = firstValue(payload, "data[user][name]", "user[name]", "data.user.name");

        TRAFFIC_LOG.info("[B24-ROUTE][ADMIN_IN][PARSED] event={} expectedBotId={} receivedBotId={} adminMessageId={} replyToAdminMessageId={} adminDialogId={} adminChatId={} userId={} userName={} userIsBot={} text={}",
                event, admin.getBotId(), botId, adminMessageId, replyToAdminMessageId, adminDialogId, adminChatId, userId, userName, userIsBot, text);

        if (text == null || text.isBlank()) {
            portalRepository.save(client);
            return Map.of("ok", true, "processed", false, "reason", "empty_text");
        }

        if (messageId != null && supportMessageRepository
                .findFirstByClientInstallation_IdAndClientMessageIdOrderByIdAsc(client.getId(), messageId)
                .isPresent()) {
            portalRepository.save(client);
            return Map.of("ok", true, "processed", false, "reason", "duplicate_message");
        }

        SupportMessage message = new SupportMessage(client);
        message.setClientDialogId(dialogId);
        message.setClientMessageId(messageId);
        message.setSenderUserId(userId);
        message.setSenderName(valueOrDefault(userName, "Пользователь " + valueOrDefault(userId, "?")));
        message.setText(text.trim());
        message.setRawEventJson(toJson(payload));
        message.setStatus("RECEIVED");
        supportMessageRepository.save(message);

        try {
            PortalInstallation admin = findReadyAdminPortal();
            String adminMessageId = forwardToAdminChat(admin, client, message);
            message.setAdminDialogId(admin.getSupportDialogId());
            message.setAdminMessageId(adminMessageId);
            message.setStatus("FORWARDED");
            supportMessageRepository.save(message);
            client.setLastClientMessageAt(now);
            markSuccess(client);
            sendClientAcknowledgement(client, dialogId);
            return Map.of("ok", true, "processed", true, "status", "FORWARDED", "messageId", message.getId());
        } catch (BitrixRestException | ResponseStatusException e) {
            String errorMessage = valueOrDefault(e.getMessage(), "Не удалось переслать сообщение в админский чат");
            message.setStatus("ERROR");
            supportMessageRepository.save(message);
            markError(client, errorMessage);
            return Map.of("ok", true, "processed", false, "status", "ERROR", "message", errorMessage);
        }
    }

    @Transactional
    public Map<String, Object> handleClientWebhookJson(String clientCode, JsonNode body) {
        return handleClientWebhook(clientCode, flattenJsonEvent(body));
    }

    @Transactional
    public Map<String, Object> handleAdminWebhook(Map<String, String> payload) {
        PortalInstallation admin = findReadyAdminPortal();
        OffsetDateTime now = OffsetDateTime.now();
        admin.setLastEventAt(now);
        admin.markUpdated();

        String event = value(payload, "event");
        if (!"ONIMBOTV2MESSAGEADD".equalsIgnoreCase(event)) {
            portalRepository.save(admin);
            return Map.of("ok", true, "processed", false, "event", valueOrDefault(event, "unknown"));
        }

        String botId = firstValue(payload, "data[bot][id]", "bot[id]", "data.bot.id");
        if (admin.getBotId() != null && botId != null && !Objects.equals(admin.getBotId(), botId)) {
            portalRepository.save(admin);
            return Map.of("ok", true, "processed", false, "reason", "bot_id_mismatch");
        }

        String userIsBot = firstValue(payload, "data[user][bot]", "user[bot]", "data.user.bot");
        if (isTruthy(userIsBot)) {
            portalRepository.save(admin);
            return Map.of("ok", true, "processed", false, "reason", "bot_author");
        }

        String adminDialogId = firstValue(payload, "data[chat][dialogId]", "chat[dialogId]", "data.chat.dialogId");
        String adminChatId = firstValue(payload, "data[message][chatId]", "message[chatId]", "data.message.chatId", "data[chat][id]", "chat[id]", "data.chat.id");
        if (!supportChatMatches(admin, adminDialogId, adminChatId)) {
            portalRepository.save(admin);
            return Map.of("ok", true, "processed", false, "reason", "not_support_chat");
        }

        String text = firstValue(payload, "data[message][text]", "message[text]", "data.message.text");
        String adminMessageId = firstValue(payload, "data[message][id]", "message[id]", "data.message.id");
        String replyToAdminMessageId = firstValue(
                payload,
                "data[message][params][REPLY_ID]",
                "data[message][params][replyId]",
                "data[message][params][reply_id]",
                "message[params][REPLY_ID]",
                "message[params][replyId]",
                "message[params][reply_id]",
                "data.message.params.REPLY_ID",
                "data.message.params.replyId",
                "data.message.params.reply_id",
                "data[message][replyId]",
                "data[message][reply_id]",
                "data[message][replyMessageId]",
                "data[message][replyToMessageId]",
                "data[message][parentId]",
                "message[replyId]",
                "message[reply_id]",
                "message[replyMessageId]",
                "message[parentId]",
                "data.message.replyId",
                "data.message.reply_id",
                "data.message.replyMessageId",
                "data.message.replyToMessageId",
                "data.message.parentId",
                "data[additionalMessages][0][id]",
                "additionalMessages[0][id]",
                "data.additionalMessages.0.id"
        );
        String userId = firstValue(payload, "data[user][id]", "user[id]", "data.user.id", "data[message][authorId]", "message[authorId]", "data.message.authorId");
        String userName = firstValue(payload, "data[user][name]", "user[name]", "data.user.name");

        TRAFFIC_LOG.info("[B24-ROUTE][ADMIN_IN][PARSED] event={} expectedBotId={} receivedBotId={} adminMessageId={} replyToAdminMessageId={} adminDialogId={} adminChatId={} userId={} userName={} userIsBot={} text={}",
                event, admin.getBotId(), botId, adminMessageId, replyToAdminMessageId, adminDialogId, adminChatId, userId, userName, userIsBot, text);

        if (text == null || text.isBlank()) {
            portalRepository.save(admin);
            return Map.of("ok", true, "processed", false, "reason", "empty_text");
        }

        if (adminMessageId != null && supportMessageRepository
                .findFirstByDirectionAndAdminMessageIdOrderByIdAsc("ADMIN_TO_CLIENT", adminMessageId)
                .isPresent()) {
            portalRepository.save(admin);
            return Map.of("ok", true, "processed", false, "reason", "duplicate_admin_message");
        }

        try {
            AdminReplyTarget target = resolveAdminReplyTarget(replyToAdminMessageId, text);
            String clientMessageId = sendReplyToClient(target.client(), target.clientDialogId(), target.text());

            SupportMessage answer = new SupportMessage(target.client());
            answer.setDirection("ADMIN_TO_CLIENT");
            answer.setClientDialogId(target.clientDialogId());
            answer.setClientMessageId(clientMessageId);
            answer.setAdminDialogId(valueOrDefault(adminDialogId, admin.getSupportDialogId()));
            answer.setAdminMessageId(adminMessageId);
            answer.setReplyToAdminMessageId(target.replyToAdminMessageId());
            answer.setSenderUserId(userId);
            answer.setSenderName(valueOrDefault(userName, "Оператор " + valueOrDefault(userId, "?")));
            answer.setText(target.text());
            answer.setRawEventJson(toJson(payload));
            answer.setStatus("SENT");
            supportMessageRepository.save(answer);

            if (target.sourceMessage() != null) {
                target.sourceMessage().setStatus("ANSWERED");
                supportMessageRepository.save(target.sourceMessage());
            }

            markSuccess(admin);
            markSuccess(target.client());
            return Map.of("ok", true, "processed", true, "status", "SENT", "messageId", answer.getId());
        } catch (BitrixRestException | ResponseStatusException e) {
            String errorMessage = valueOrDefault(e.getMessage(), "Не удалось отправить ответ клиенту");
            markError(admin, errorMessage);
            return Map.of("ok", true, "processed", false, "status", "ERROR", "message", errorMessage);
        }
    }

    @Transactional
    public Map<String, Object> handleAdminWebhookJson(JsonNode body) {
        return handleAdminWebhook(flattenJsonEvent(body));
    }

    private AdminReplyTarget resolveAdminReplyTarget(String replyToAdminMessageId, String text) {
        String answerText = text.trim();

        if (replyToAdminMessageId != null && !replyToAdminMessageId.isBlank()) {
            TRAFFIC_LOG.info("[B24-ROUTE][RESOLVE_REPLY][BY_REPLY_ID][START] replyToAdminMessageId={} text={}", replyToAdminMessageId, answerText);
            SupportMessage source = supportMessageRepository
                    .findFirstByDirectionAndAdminMessageIdOrderByIdAsc("CLIENT_TO_ADMIN", replyToAdminMessageId)
                    .orElse(null);
            if (source != null) {
                TRAFFIC_LOG.info("[B24-ROUTE][RESOLVE_REPLY][BY_REPLY_ID][FOUND] replyToAdminMessageId={} sourceMessageId={} clientId={} clientCode={} clientDialogId={}",
                        replyToAdminMessageId, source.getId(), source.getClientInstallation().getId(), source.getClientInstallation().getClientCode(), source.getClientDialogId());
                requireClientReplyTarget(source.getClientInstallation(), source.getClientDialogId());
                return new AdminReplyTarget(
                        source.getClientInstallation(),
                        source.getClientDialogId(),
                        replyToAdminMessageId,
                        answerText,
                        source
                );
            }
            TRAFFIC_LOG.warn("[B24-ROUTE][RESOLVE_REPLY][BY_REPLY_ID][NOT_FOUND] replyToAdminMessageId={} text={}", replyToAdminMessageId, answerText);
        }

        Matcher matcher = CLIENT_CODE_COMMAND.matcher(answerText);
        if (!matcher.matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Не найдено исходное обращение. Ответь reply на сообщение клиента или напиши #код_клиента текст ответа"
            );
        }

        String clientCode = matcher.group(1).trim();
        String cleanText = matcher.group(2).trim();
        TRAFFIC_LOG.info("[B24-ROUTE][RESOLVE_REPLY][BY_CLIENT_CODE][START] clientCode={} cleanText={}", clientCode, cleanText);
        PortalInstallation client = portalRepository.findByClientCode(clientCode)
                .filter(portal -> portal.getRole() == PortalRole.CLIENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Клиент с кодом #" + clientCode + " не найден"));
        SupportMessage latest = supportMessageRepository
                .findFirstByClientInstallation_IdAndClientDialogIdIsNotNullOrderByCreatedAtDesc(client.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "У клиента #" + clientCode + " ещё нет активного диалога"));

        requireClientReplyTarget(client, latest.getClientDialogId());
        return new AdminReplyTarget(client, latest.getClientDialogId(), null, cleanText, latest);
    }

    private String sendReplyToClient(PortalInstallation client, String clientDialogId, String text) {
        requireClientReplyTarget(client, clientDialogId);

        String message = "[B]Ответ техподдержки «Умные продажи»:[/B]\n" + text;
        Map<String, Object> payload = Map.of(
                "botId", parseInteger(client.getBotId(), "Client Bot ID"),
                "botToken", client.getBotToken(),
                "dialogId", clientDialogId,
                "fields", Map.of(
                        "message", message,
                        "urlPreview", false
                )
        );

        JsonNode root = bitrixRestClient.callJson(client.getWebhookUrl(), "imbot.v2.Chat.Message.send", payload);
        return extractMessageId(root);
    }

    private void requireClientReplyTarget(PortalInstallation client, String clientDialogId) {
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Клиентский портал для ответа не найден");
        }
        if (client.getWebhookUrl() == null || client.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У клиентского портала не заполнен webhook");
        }
        if (client.getBotId() == null || client.getBotToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У клиентского портала не создан бот");
        }
        if (clientDialogId == null || clientDialogId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не найден dialogId клиентского диалога");
        }
    }

    private boolean supportChatMatches(PortalInstallation admin, String dialogId, String chatId) {
        if (dialogId != null && !dialogId.isBlank()) {
            return Objects.equals(admin.getSupportDialogId(), dialogId);
        }
        if (chatId != null && !chatId.isBlank() && admin.getSupportChatId() != null && !admin.getSupportChatId().isBlank()) {
            return Objects.equals(admin.getSupportChatId(), chatId);
        }
        return true;
    }

    private void configureExistingClientBotWebhook(PortalInstallation client, String webhookUrl) {
        updateClientBotWebhook(client, webhookUrl);
        client.setBotCode(valueOrDefault(client.getBotCode(), CLIENT_BOT_CODE));
        client.setBotType(CLIENT_BOT_TYPE);
        client.setBotEventWebhookUrl(webhookUrl);
        client.setBotRegisteredAt(OffsetDateTime.now());
        client.markUpdated();
    }

    private void updateClientBotWebhook(PortalInstallation client, String webhookUrl) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventMode", "webhook");
        fields.put("webhookUrl", webhookUrl);
        fields.put("type", CLIENT_BOT_TYPE);
        fields.put("properties", Map.of(
                "name", CLIENT_BOT_NAME,
                "workPosition", "Помощник технической поддержки"
        ));

        Map<String, Object> payload = Map.of(
                "botId", parseInteger(client.getBotId(), "Bot ID"),
                "botToken", client.getBotToken(),
                "fields", fields
        );
        bitrixRestClient.callJson(client.getWebhookUrl(), "imbot.v2.Bot.update", payload);
    }

    private String forwardToAdminChat(PortalInstallation admin, PortalInstallation client, SupportMessage message) {
        String text = "[B]Новое обращение клиента[/B]\n"
                + "Портал: " + client.getTitle() + " (#" + client.getClientCode() + ")\n"
                + "Домен: " + client.getDomain() + "\n"
                + "Клиент: " + valueOrDefault(message.getSenderName(), "неизвестно")
                + " (ID " + valueOrDefault(message.getSenderUserId(), "?") + ")\n"
                + "Диалог клиента: " + valueOrDefault(message.getClientDialogId(), "?") + "\n\n"
                + "[B]Сообщение:[/B]\n"
                + message.getText() + "\n\n"
                + "[SIZE=11]Чтобы ответить клиенту, нажми reply на это сообщение и напиши ответ. "
                + "Запасной вариант: #" + client.getClientCode() + " текст ответа[/SIZE]";

        Map<String, Object> payload = Map.of(
                "botId", parseInteger(admin.getBotId(), "Admin Bot ID"),
                "botToken", admin.getBotToken(),
                "dialogId", admin.getSupportDialogId(),
                "fields", Map.of(
                        "message", text,
                        "urlPreview", false
                )
        );

        JsonNode root = bitrixRestClient.callJson(admin.getWebhookUrl(), "imbot.v2.Chat.Message.send", payload);
        return extractMessageId(root);
    }

    private void sendClientAcknowledgement(PortalInstallation client, String dialogId) {
        if (dialogId == null || dialogId.isBlank()) {
            return;
        }
        if (client.getBotId() == null || client.getBotToken() == null) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "botId", parseInteger(client.getBotId(), "Client Bot ID"),
                "botToken", client.getBotToken(),
                "dialogId", dialogId,
                "fields", Map.of(
                        "message", "Спасибо, обращение передано в техподдержку «Умные продажи». Оператор ответит здесь после обработки.",
                        "urlPreview", false
                )
        );
        bitrixRestClient.callJson(client.getWebhookUrl(), "imbot.v2.Chat.Message.send", payload);
    }

    private PortalInstallation findReadyAdminPortal() {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Админский портал не подключён"));
        if (admin.getWebhookUrl() == null || admin.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У админского портала не заполнен webhook");
        }
        if (admin.getBotId() == null || admin.getBotToken() == null || admin.getSupportDialogId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала создай админского бота и админский чат");
        }
        return admin;
    }

    private PortalInstallation findClientPortal(Long portalId) {
        PortalInstallation portal = portalRepository.findById(portalId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Портал не найден"
        ));
        if (portal.getRole() != PortalRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Операция доступна только для клиентского портала");
        }
        return portal;
    }

    private void requireWebhook(PortalInstallation portal) {
        if (portal.getWebhookUrl() == null || portal.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У клиентского портала не заполнен Webhook / REST URL");
        }
    }

    private void requireClientBot(PortalInstallation client) {
        if (client.getBotId() == null || client.getBotId().isBlank() || client.getBotToken() == null || client.getBotToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала создай клиентского бота");
        }
    }

    private String buildClientEventWebhookUrl(PortalInstallation client) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "app.public-base-url не настроен");
        }
        return base + "/api/bitrix/events/client/" + client.getClientCode();
    }

    private Map<String, String> flattenJsonEvent(JsonNode body) {
        Map<String, String> result = new LinkedHashMap<>();
        put(result, "event", body.path("event"));
        put(result, "auth[application_token]", body.path("auth").path("application_token"));
        put(result, "auth[domain]", body.path("auth").path("domain"));
        put(result, "data[bot][id]", body.path("data").path("bot").path("id"));
        put(result, "data[bot][code]", body.path("data").path("bot").path("code"));
        put(result, "data[message][id]", body.path("data").path("message").path("id"));
        put(result, "data[message][chatId]", body.path("data").path("message").path("chatId"));
        put(result, "data[message][authorId]", body.path("data").path("message").path("authorId"));
        put(result, "data[message][text]", body.path("data").path("message").path("text"));
        put(result, "data[chat][id]", body.path("data").path("chat").path("id"));
        put(result, "data[chat][dialogId]", body.path("data").path("chat").path("dialogId"));
        put(result, "data[user][id]", body.path("data").path("user").path("id"));
        put(result, "data[user][name]", body.path("data").path("user").path("name"));
        put(result, "data[user][bot]", body.path("data").path("user").path("bot"));
        put(result, "data[message][replyId]", body.path("data").path("message").path("replyId"));
        put(result, "data[message][reply_id]", body.path("data").path("message").path("reply_id"));
        put(result, "data[message][replyMessageId]", body.path("data").path("message").path("replyMessageId"));
        put(result, "data[message][replyToMessageId]", body.path("data").path("message").path("replyToMessageId"));
        put(result, "data[message][parentId]", body.path("data").path("message").path("parentId"));
        put(result, "data[message][params][REPLY_ID]", body.path("data").path("message").path("params").path("REPLY_ID"));
        put(result, "data[message][params][replyId]", body.path("data").path("message").path("params").path("replyId"));
        put(result, "data[message][params][reply_id]", body.path("data").path("message").path("params").path("reply_id"));
        put(result, "data[additionalMessages][0][id]", body.path("data").path("additionalMessages").path(0).path("id"));
        return result;
    }

    private void put(Map<String, String> target, String key, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        target.put(key, value.asText());
    }

    private String firstValue(Map<String, String> payload, String... keys) {
        for (String key : keys) {
            String value = value(payload, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String value(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "y".equals(normalized) || "yes".equals(normalized) || "true".equals(normalized);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String extractMessageId(JsonNode root) {
        JsonNode result = root.path("result");
        String id = text(result, "id");
        if (id != null) {
            return id;
        }
        if (!result.isMissingNode() && !result.isNull()) {
            String asText = result.asText(null);
            if (asText != null && !asText.isBlank()) {
                return asText.trim();
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String generateBotToken() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Integer parseInteger(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " не заполнен или некорректен");
        }
    }

    private void markSuccess(PortalInstallation portal) {
        portal.setStatus(PortalStatus.ACTIVE);
        portal.setConnectedAt(OffsetDateTime.now());
        portal.setLastError(null);
        portal.markUpdated();
        portalRepository.save(portal);
    }

    private void markError(PortalInstallation portal, String message) {
        portal.setStatus(PortalStatus.ERROR);
        portal.setLastError(message);
        portal.markUpdated();
        portalRepository.save(portal);
    }

    private ClientPortalActionResponse response(boolean success, String message, PortalInstallation portal) {
        return new ClientPortalActionResponse(success, message, PortalInstallationResponse.from(portal));
    }

    private record AdminReplyTarget(
            PortalInstallation client,
            String clientDialogId,
            String replyToAdminMessageId,
            String text,
            SupportMessage sourceMessage
    ) {
    }
}
