package ru.abs7.b24support.api.dto;

import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.PortalStatus;
import java.time.OffsetDateTime;

public record PortalInstallationResponse(
        Long id,
        PortalRole role,
        String clientCode,
        String title,
        String domain,
        String memberId,
        String webhookUrl,
        boolean webhookConfigured,
        String botId,
        String botCode,
        boolean botTokenConfigured,
        String botTokenMasked,
        String botType,
        String supportChatId,
        String supportDialogId,
        String botEventWebhookUrl,
        OffsetDateTime lastEventAt,
        OffsetDateTime lastClientMessageAt,
        String lastError,
        OffsetDateTime connectedAt,
        OffsetDateTime botRegisteredAt,
        OffsetDateTime supportChatCreatedAt,
        PortalStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PortalInstallationResponse from(PortalInstallation installation) {
        return new PortalInstallationResponse(
                installation.getId(),
                installation.getRole(),
                installation.getClientCode(),
                installation.getTitle(),
                installation.getDomain(),
                installation.getMemberId(),
                installation.getWebhookUrl(),
                installation.getWebhookUrl() != null && !installation.getWebhookUrl().isBlank(),
                installation.getBotId(),
                installation.getBotCode(),
                installation.getBotToken() != null && !installation.getBotToken().isBlank(),
                maskToken(installation.getBotToken()),
                installation.getBotType(),
                installation.getSupportChatId(),
                installation.getSupportDialogId(),
                installation.getBotEventWebhookUrl(),
                installation.getLastEventAt(),
                installation.getLastClientMessageAt(),
                installation.getLastError(),
                installation.getConnectedAt(),
                installation.getBotRegisteredAt(),
                installation.getSupportChatCreatedAt(),
                installation.getStatus(),
                installation.getCreatedAt(),
                installation.getUpdatedAt()
        );
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String clean = token.trim();
        if (clean.length() <= 8) {
            return "••••";
        }
        return clean.substring(0, 4) + "••••" + clean.substring(clean.length() - 4);
    }
}
