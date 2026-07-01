package ru.abs7.b24support.api.dto;

import java.util.List;

public record PortalInstallationListResponse(
        long total,
        long adminCount,
        long clientCount,
        List<PortalInstallationResponse> items
) {
}
