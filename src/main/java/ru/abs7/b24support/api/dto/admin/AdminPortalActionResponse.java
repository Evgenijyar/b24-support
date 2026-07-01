package ru.abs7.b24support.api.dto.admin;

import ru.abs7.b24support.api.dto.PortalInstallationResponse;

public record AdminPortalActionResponse(
        boolean success,
        String message,
        PortalInstallationResponse portal,
        long loadedUsers,
        long supportMembers
) {
}
