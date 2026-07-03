package ru.abs7.b24support.bitrix;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class BitrixRestClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BitrixRestClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
        this.objectMapper = objectMapper;
    }

    public JsonNode call(String webhookUrl, String method) {
        return call(webhookUrl, method, Map.of());
    }

    public JsonNode call(String webhookUrl, String method, Map<String, String> params) {
        String endpoint = buildEndpoint(webhookUrl, method);
        String body = encode(params == null ? Map.of() : params);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    public JsonNode callJson(String webhookUrl, String method, Object payload) {
        String endpoint = buildEndpoint(webhookUrl, method);
        String body;
        try {
            body = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            throw new BitrixRestException("Не удалось подготовить JSON-запрос к Bitrix24", e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BitrixRestException("Bitrix24 вернул HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                String error = root.path("error").asText();
                String description = root.path("error_description").asText();
                throw new BitrixRestException((description == null || description.isBlank()) ? error : description);
            }

            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BitrixRestException("REST-запрос к Bitrix24 был прерван", e);
        } catch (IllegalArgumentException e) {
            throw new BitrixRestException("Некорректный webhook URL Bitrix24", e);
        } catch (IOException e) {
            throw new BitrixRestException("Не удалось прочитать ответ Bitrix24", e);
        } catch (BitrixRestException e) {
            throw e;
        } catch (Exception e) {
            throw new BitrixRestException("Не удалось обработать JSON-ответ Bitrix24", e);
        }
    }

    private String buildEndpoint(String webhookUrl, String method) {
        String base = webhookUrl == null ? "" : webhookUrl.trim();
        if (base.isBlank()) {
            throw new BitrixRestException("Webhook URL не заполнен");
        }

        if (!base.endsWith("/")) {
            base += "/";
        }

        String cleanMethod = method == null ? "" : method.trim();
        if (cleanMethod.isBlank()) {
            throw new BitrixRestException("REST-метод Bitrix24 не указан");
        }
        if (cleanMethod.endsWith(".json")) {
            cleanMethod = cleanMethod.substring(0, cleanMethod.length() - ".json".length());
        }

        return base + cleanMethod + ".json";
    }

    private String encode(Map<String, String> params) {
        Map<String, String> safeParams = new LinkedHashMap<>(params);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : safeParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
