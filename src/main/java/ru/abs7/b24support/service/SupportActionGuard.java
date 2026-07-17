package ru.abs7.b24support.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class SupportActionGuard {

    private final String configuredKey;

    public SupportActionGuard(@Value("${app.action-api-key:}") String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
    }

    public void requireValidKey(String suppliedKey) {
        if (configuredKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "APP_ACTION_API_KEY не настроен. Операции изменения обращений отключены"
            );
        }
        String candidate = suppliedKey == null ? "" : suppliedKey.trim();
        if (!MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Неверный ключ операций поддержки");
        }
    }
}
