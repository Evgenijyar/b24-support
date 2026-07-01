package ru.abs7.b24support.api.dto.admin;

import java.util.List;

public record BitrixUserListResponse(
        Long portalId,
        long total,
        long supportMembers,
        List<BitrixUserResponse> users
) {
}
