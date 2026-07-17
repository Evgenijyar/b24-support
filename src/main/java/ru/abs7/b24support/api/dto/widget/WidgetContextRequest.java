package ru.abs7.b24support.api.dto.widget;

import jakarta.validation.constraints.NotBlank;

public record WidgetContextRequest(
        @NotBlank String dialogId,
        @NotBlank String domain,
        String memberId,
        @NotBlank String accessToken
) {
}
