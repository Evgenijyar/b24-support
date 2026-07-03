package ru.abs7.b24support.api.dto.client;

import ru.abs7.b24support.api.dto.PortalInstallationResponse;

public record ClientPortalActionResponse(
        boolean success,
        String message,
        PortalInstallationResponse portal
) {
}
