package ru.abs7.b24support.api.dto.conversation;

import java.util.List;

public record SupportConversationListResponse(
        int total,
        List<SupportConversationSummaryResponse> items
) {
}
