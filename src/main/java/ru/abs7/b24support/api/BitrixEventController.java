package ru.abs7.b24support.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.service.ClientPortalService;
import tools.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/bitrix/events")
public class BitrixEventController {

    private static final Logger TRAFFIC_LOG = LoggerFactory.getLogger("BITRIX_TRAFFIC");
    private static final AtomicLong WEBHOOK_SEQUENCE = new AtomicLong();

    private final ClientPortalService clientPortalService;

    public BitrixEventController(ClientPortalService clientPortalService) {
        this.clientPortalService = clientPortalService;
    }

    @PostMapping(value = "/admin", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> adminEventForm(@RequestParam MultiValueMap<String, String> form) {
        long requestId = WEBHOOK_SEQUENCE.incrementAndGet();
        Map<String, String> payload = new LinkedHashMap<>();
        form.forEach((key, values) -> payload.put(key, values == null || values.isEmpty() ? null : values.get(0)));
        TRAFFIC_LOG.info("[B24-WEBHOOK][{}][ADMIN][FORM][IN] keys={} payload={}", requestId, payload.keySet(), maskPayload(payload));
        try {
            Map<String, Object> result = clientPortalService.handleAdminWebhook(payload);
            TRAFFIC_LOG.info("[B24-WEBHOOK][{}][ADMIN][FORM][OUT] result={}", requestId, result);
            return result;
        } catch (RuntimeException e) {
            TRAFFIC_LOG.error("[B24-WEBHOOK][{}][ADMIN][FORM][EXCEPTION] payload={}", requestId, maskPayload(payload), e);
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    @PostMapping(value = "/admin", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> adminEventJson(@RequestBody JsonNode body) {
        long requestId = WEBHOOK_SEQUENCE.incrementAndGet();
        TRAFFIC_LOG.info("[B24-WEBHOOK][{}][ADMIN][JSON][IN] body={}", requestId, body);
        try {
            Map<String, Object> result = clientPortalService.handleAdminWebhookJson(body);
            TRAFFIC_LOG.info("[B24-WEBHOOK][{}][ADMIN][JSON][OUT] result={}", requestId, result);
            return result;
        } catch (RuntimeException e) {
            TRAFFIC_LOG.error("[B24-WEBHOOK][{}][ADMIN][JSON][EXCEPTION] body={}", requestId, body, e);
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    @PostMapping(value = "/client/{clientCode}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> clientEventForm(@PathVariable String clientCode,
                                               @RequestParam MultiValueMap<String, String> form) {
        long requestId = WEBHOOK_SEQUENCE.incrementAndGet();
        Map<String, String> payload = new LinkedHashMap<>();
        form.forEach((key, values) -> payload.put(key, values == null || values.isEmpty() ? null : values.get(0)));
        TRAFFIC_LOG.info("[B24-WEBHOOK][{}][CLIENT][FORM][IN] clientCode={} keys={} payload={}", requestId, clientCode, payload.keySet(), maskPayload(payload));
        try {
            Map<String, Object> result = clientPortalService.handleClientWebhook(clientCode, payload);
            TRAFFIC_LOG.info("[B24-WEBHOOK][{}][CLIENT][FORM][OUT] clientCode={} result={}", requestId, clientCode, result);
            return result;
        } catch (RuntimeException e) {
            TRAFFIC_LOG.error("[B24-WEBHOOK][{}][CLIENT][FORM][EXCEPTION] clientCode={} payload={}", requestId, clientCode, maskPayload(payload), e);
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    @PostMapping(value = "/client/{clientCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> clientEventJson(@PathVariable String clientCode,
                                               @RequestBody JsonNode body) {
        long requestId = WEBHOOK_SEQUENCE.incrementAndGet();
        TRAFFIC_LOG.info("[B24-WEBHOOK][{}][CLIENT][JSON][IN] clientCode={} body={}", requestId, clientCode, body);
        try {
            Map<String, Object> result = clientPortalService.handleClientWebhookJson(clientCode, body);
            TRAFFIC_LOG.info("[B24-WEBHOOK][{}][CLIENT][JSON][OUT] clientCode={} result={}", requestId, clientCode, result);
            return result;
        } catch (RuntimeException e) {
            TRAFFIC_LOG.error("[B24-WEBHOOK][{}][CLIENT][JSON][EXCEPTION] clientCode={} body={}", requestId, clientCode, body, e);
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    private Map<String, String> maskPayload(Map<String, String> payload) {
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (isSecretKey(key)) {
                masked.put(key, "***");
            } else {
                masked.put(key, entry.getValue());
            }
        }
        return masked;
    }

    private boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return normalized.contains("token")
                || normalized.contains("application_token")
                || normalized.contains("access_token")
                || normalized.contains("refresh_token")
                || normalized.contains("auth");
    }

}
