package ru.abs7.b24support.api.dto.crm;

public record CrmSmartProcessOption(
        Integer entityTypeId,
        String title,
        boolean stagesEnabled,
        boolean categoriesEnabled,
        boolean clientEnabled,
        boolean eligible,
        String unavailableReason
) {
}
