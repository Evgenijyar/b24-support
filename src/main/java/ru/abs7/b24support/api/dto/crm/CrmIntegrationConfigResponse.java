package ru.abs7.b24support.api.dto.crm;

import ru.abs7.b24support.domain.CrmIntegrationConfig;
import java.time.OffsetDateTime;

public record CrmIntegrationConfigResponse(
        boolean configured,
        boolean enabled,
        Integer entityTypeId,
        String processTitle,
        Integer categoryId,
        String categoryTitle,
        String openStageId,
        String openStageTitle,
        String closedStageId,
        String closedStageTitle,
        String responsibleUserId,
        String responsibleUserName,
        OffsetDateTime configuredAt,
        OffsetDateTime lastValidatedAt,
        String lastError
) {
    public static CrmIntegrationConfigResponse empty() {
        return new CrmIntegrationConfigResponse(
                false, false, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null
        );
    }

    public static CrmIntegrationConfigResponse from(CrmIntegrationConfig config) {
        return new CrmIntegrationConfigResponse(
                true,
                config.isEnabled(),
                config.getEntityTypeId(),
                config.getProcessTitle(),
                config.getCategoryId(),
                config.getCategoryTitle(),
                config.getOpenStageId(),
                config.getOpenStageTitle(),
                config.getClosedStageId(),
                config.getClosedStageTitle(),
                config.getResponsibleUserId(),
                config.getResponsibleUserName(),
                config.getConfiguredAt(),
                config.getLastValidatedAt(),
                config.getLastError()
        );
    }
}
