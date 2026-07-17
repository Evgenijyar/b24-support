package ru.abs7.b24support.bitrix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicLong;

public class BitrixRestClient {

    private static final Logger TRAFFIC_LOG = LoggerFactory.getLogger("BITRIX_TRAFFIC");
    private static final AtomicLong REST_SEQUENCE = new AtomicLong();

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

        return send(request, method, endpoint, body, "FORM");
    }


    public JsonNode callOAuth(String portalDomain, String method, String accessToken, Map<String, String> params) {
        String endpoint = buildOAuthEndpoint(portalDomain, method);
        Map<String, String> bodyParams = new LinkedHashMap<>();
        if (params != null) {
            bodyParams.putAll(params);
        }
        bodyParams.put("auth", accessToken);
        String body = encode(bodyParams);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return send(request, method, endpoint, body, "OAUTH_FORM");
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

        return send(request, method, endpoint, body, "JSON");
    }

    private JsonNode send(HttpRequest request, String method, String endpoint, String requestBody, String bodyType) {
        long restId = REST_SEQUENCE.incrementAndGet();
        long startedAt = System.currentTimeMillis();
        String safeEndpoint = maskEndpoint(endpoint);
        String safeRequestBody = maskSecrets(requestBody);

        TRAFFIC_LOG.info("[B24-REST][{}][REQUEST] method={} bodyType={} endpoint={} body={}",
                restId,
                method,
                bodyType,
                safeEndpoint,
                safeRequestBody);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = System.currentTimeMillis() - startedAt;
            String responseBody = response.body() == null ? "" : response.body();

            TRAFFIC_LOG.info("[B24-REST][{}][RESPONSE] method={} status={} durationMs={} body={}",
                    restId,
                    method,
                    response.statusCode(),
                    durationMs,
                    maskSecrets(responseBody));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                TRAFFIC_LOG.error("[B24-REST][{}][HTTP_ERROR] method={} status={} endpoint={} requestBody={} responseBody={}",
                        restId,
                        method,
                        response.statusCode(),
                        safeEndpoint,
                        safeRequestBody,
                        maskSecrets(responseBody));
                throw new BitrixRestException("Bitrix24 вернул HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("error")) {
                String error = root.path("error").asText();
                String description = root.path("error_description").asText();
                TRAFFIC_LOG.error("[B24-REST][{}][BITRIX_ERROR] method={} error={} description={} endpoint={} requestBody={} responseBody={}",
                        restId,
                        method,
                        error,
                        description,
                        safeEndpoint,
                        safeRequestBody,
                        maskSecrets(responseBody));
                throw new BitrixRestException((description == null || description.isBlank()) ? error : description);
            }

            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            TRAFFIC_LOG.error("[B24-REST][{}][INTERRUPTED] method={} endpoint={} requestBody={}",
                    restId,
                    method,
                    safeEndpoint,
                    safeRequestBody,
                    e);
            throw new BitrixRestException("REST-запрос к Bitrix24 был прерван", e);
        } catch (IllegalArgumentException e) {
            TRAFFIC_LOG.error("[B24-REST][{}][BAD_URL] method={} endpoint={} requestBody={}",
                    restId,
                    method,
                    safeEndpoint,
                    safeRequestBody,
                    e);
            throw new BitrixRestException("Некорректный webhook URL Bitrix24", e);
        } catch (IOException e) {
            TRAFFIC_LOG.error("[B24-REST][{}][IO_ERROR] method={} endpoint={} requestBody={}",
                    restId,
                    method,
                    safeEndpoint,
                    safeRequestBody,
                    e);
            throw new BitrixRestException("Не удалось прочитать ответ Bitrix24", e);
        } catch (BitrixRestException e) {
            throw e;
        } catch (Exception e) {
            TRAFFIC_LOG.error("[B24-REST][{}][JSON_ERROR] method={} endpoint={} requestBody={}",
                    restId,
                    method,
                    safeEndpoint,
                    safeRequestBody,
                    e);
            throw new BitrixRestException("Не удалось обработать JSON-ответ Bitrix24", e);
        }
    }


    private String buildOAuthEndpoint(String portalDomain, String method) {
        String host = portalDomain == null ? "" : portalDomain.trim().toLowerCase();
        host = host.replaceFirst("^https?://", "").replaceAll("/+$", "");
        if (host.isBlank() || !host.matches("[a-z0-9.-]+(?::[0-9]{1,5})?")) {
            throw new BitrixRestException("Некорректный домен Bitrix24");
        }

        String cleanMethod = method == null ? "" : method.trim();
        if (cleanMethod.isBlank()) {
            throw new BitrixRestException("REST-метод Bitrix24 не указан");
        }
        if (cleanMethod.endsWith(".json")) {
            cleanMethod = cleanMethod.substring(0, cleanMethod.length() - ".json".length());
        }
        if (!cleanMethod.matches("[A-Za-z0-9._]+")) {
            throw new BitrixRestException("Некорректный REST-метод Bitrix24");
        }

        return "https://" + host + "/rest/" + cleanMethod + ".json";
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

    private String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        return endpoint.replaceAll("/rest/[^/]+/[^/]+/", "/rest/***/***/");
    }

    private String maskSecrets(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = value;
        masked = masked.replaceAll("(?i)(\\\"botToken\\\"\\s*:\\s*\\\")[^\\\"]+", "$1***");
        masked = masked.replaceAll("(?i)(botToken=)[^&\\s]+", "$1***");
        masked = masked.replaceAll("(?i)(application_token%5D=)[^&\\s]+", "$1***");
        masked = masked.replaceAll("(?i)(application_token=)[^&\\s]+", "$1***");
        masked = masked.replaceAll("(?i)(access_token=)[^&\\s]+", "$1***");
        masked = masked.replaceAll("(?i)(auth=)[^&\\s]+", "$1***");
        masked = masked.replaceAll("(?i)(auth%5Baccess_token%5D=)[^&\\s]+", "$1***");
        masked = masked.replaceAll("(?i)(refresh_token=)[^&\\s]+", "$1***");
        return masked;
    }
}
