package ru.abs7.b24support.api;

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

@RestController
@RequestMapping("/api/bitrix/events")
public class BitrixEventController {

    private final ClientPortalService clientPortalService;

    public BitrixEventController(ClientPortalService clientPortalService) {
        this.clientPortalService = clientPortalService;
    }

    @PostMapping(value = "/admin", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> adminEventForm(@RequestParam MultiValueMap<String, String> form) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            form.forEach((key, values) -> payload.put(key, values == null || values.isEmpty() ? null : values.get(0)));
            return clientPortalService.handleAdminWebhook(payload);
        } catch (RuntimeException e) {
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    @PostMapping(value = "/admin", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> adminEventJson(@RequestBody JsonNode body) {
        try {
            return clientPortalService.handleAdminWebhookJson(body);
        } catch (RuntimeException e) {
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    @PostMapping(value = "/client/{clientCode}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> clientEventForm(@PathVariable String clientCode,
                                               @RequestParam MultiValueMap<String, String> form) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            form.forEach((key, values) -> payload.put(key, values == null || values.isEmpty() ? null : values.get(0)));
            return clientPortalService.handleClientWebhook(clientCode, payload);
        } catch (RuntimeException e) {
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }

    @PostMapping(value = "/client/{clientCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> clientEventJson(@PathVariable String clientCode,
                                               @RequestBody JsonNode body) {
        try {
            return clientPortalService.handleClientWebhookJson(clientCode, body);
        } catch (RuntimeException e) {
            return Map.of("ok", true, "processed", false, "error", e.getMessage() == null ? "internal_error" : e.getMessage());
        }
    }
}
