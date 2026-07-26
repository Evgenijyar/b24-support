package ru.abs7.b24support.api.dto.conversation;

import java.util.List;

public record SupportConversationMessageListResponse(
        Long ticketId,
        int total,
        List<SupportConversationMessageResponse> items
) {
}
