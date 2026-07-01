package ru.abs7.b24support.api.dto.admin;

import ru.abs7.b24support.api.dto.PortalInstallationResponse;

public record AdminPortalSummaryResponse(
        PortalInstallationResponse adminPortal,
        long loadedUsers,
        long supportMembers
) {
}
