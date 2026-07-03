package ru.abs7.b24support.api.dto.client;

import java.util.List;

public record SupportMessageListResponse(
        int total,
        List<SupportMessageResponse> items
) {
}
