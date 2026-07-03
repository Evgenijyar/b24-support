package ru.abs7.b24support.api.dto.client;

import ru.abs7.b24support.domain.SupportMessage;
import java.time.OffsetDateTime;

public record SupportMessageResponse(
        Long id,
        Long clientPortalId,
        String clientPortalTitle,
        String clientCode,
        String clientDialogId,
        String clientMessageId,
        String adminDialogId,
        String adminMessageId,
        String senderUserId,
        String senderName,
        String text,
        String status,
        OffsetDateTime createdAt
) {
    public static SupportMessageResponse from(SupportMessage message) {
        var client = message.getClientInstallation();
        return new SupportMessageResponse(
                message.getId(),
                client == null ? null : client.getId(),
                client == null ? null : client.getTitle(),
                client == null ? null : client.getClientCode(),
                message.getClientDialogId(),
                message.getClientMessageId(),
                message.getAdminDialogId(),
                message.getAdminMessageId(),
                message.getSenderUserId(),
                message.getSenderName(),
                message.getText(),
                message.getStatus(),
                message.getCreatedAt()
        );
    }
}
