package ru.abs7.b24support.api.dto.crm;

public record CrmValidationResponse(
        boolean success,
        String message,
        CrmIntegrationConfigResponse config
) {
}
