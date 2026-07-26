package ru.abs7.b24support.api.dto.conversation;

import java.time.OffsetDateTime;

public record SupportConversationSummaryResponse(
        Long ticketId,
        Long clientPortalId,
        String clientTitle,
        Long sequenceNumber,
        String status,
        String requesterName,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime lastMessageAt,
        String lastMessagePreview,
        int messageCount
) {
}
