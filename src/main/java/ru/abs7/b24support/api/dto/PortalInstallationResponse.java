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
        String supportDialogId,
        String lastError,
        OffsetDateTime connectedAt,
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
                installation.getSupportDialogId(),
                installation.getLastError(),
                installation.getConnectedAt(),
                installation.getStatus(),
                installation.getCreatedAt(),
                installation.getUpdatedAt()
        );
    }
}
