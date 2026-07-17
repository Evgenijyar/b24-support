package ru.abs7.b24support.api.dto.ticket;

import ru.abs7.b24support.domain.SupportSettings;
import java.time.OffsetDateTime;

public record SupportSettingsResponse(int closedChatRetentionDays, OffsetDateTime updatedAt) {
    public static SupportSettingsResponse from(SupportSettings settings) {
        return new SupportSettingsResponse(settings.getClosedChatRetentionDays(), settings.getUpdatedAt());
    }
}
