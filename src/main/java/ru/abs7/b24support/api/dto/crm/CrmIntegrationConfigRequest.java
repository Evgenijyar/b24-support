package ru.abs7.b24support.api.dto.crm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrmIntegrationConfigRequest(
        @NotNull(message = "Выбери смарт-процесс")
        Integer entityTypeId,

        @NotNull(message = "Выбери воронку")
        Integer categoryId,

        @NotBlank(message = "Выбери стадию открытого обращения")
        String openStageId,

        @NotBlank(message = "Выбери стадию завершённого обращения")
        String closedStageId,

        @NotBlank(message = "Выбери ответственного")
        String responsibleUserId
) {
}
