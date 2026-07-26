package ru.abs7.b24support.api.dto.conversation;

import ru.abs7.b24support.domain.SupportMessage;
import java.time.OffsetDateTime;

public record SupportConversationMessageResponse(
        Long id,
        String direction,
        String senderUserId,
        String senderName,
        String text,
        String status,
        OffsetDateTime createdAt
) {
    public static SupportConversationMessageResponse from(SupportMessage message) {
        return new SupportConversationMessageResponse(
                message.getId(),
                message.getDirection(),
                message.getSenderUserId(),
                message.getSenderName(),
                message.getText(),
                message.getStatus(),
                message.getCreatedAt()
        );
    }
}
