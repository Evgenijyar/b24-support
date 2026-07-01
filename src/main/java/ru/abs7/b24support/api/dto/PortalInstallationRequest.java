package ru.abs7.b24support.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.PortalStatus;

public record PortalInstallationRequest(
        @NotNull(message = "Укажите роль портала")
        PortalRole role,

        @Size(max = 64, message = "Код клиента не должен быть длиннее 64 символов")
        String clientCode,

        @NotBlank(message = "Укажите название портала")
        @Size(max = 255, message = "Название не должно быть длиннее 255 символов")
        String title,

        @NotBlank(message = "Укажите домен Bitrix24")
        @Size(max = 255, message = "Домен не должен быть длиннее 255 символов")
        String domain,

        @Size(max = 255, message = "member_id не должен быть длиннее 255 символов")
        String memberId,

        @Size(max = 2048, message = "Webhook URL слишком длинный")
        String webhookUrl,

        @Size(max = 255, message = "Bot ID не должен быть длиннее 255 символов")
        String botId,

        @Size(max = 255, message = "Dialog ID не должен быть длиннее 255 символов")
        String supportDialogId,

        PortalStatus status
) {
}
