package ru.abs7.b24support.api.dto.ticket;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SupportSettingsRequest(
        @Min(1) @Max(3650) int closedChatRetentionDays
) {
}
