package ru.abs7.b24support.api.dto.widget;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WidgetActionRequest(
        @NotBlank String dialogId,
        @NotBlank String domain,
        String memberId,
        @NotBlank String accessToken,
        @NotNull WidgetActionType action
) {
}
