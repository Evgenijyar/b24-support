package ru.abs7.b24support.api.dto.admin;

import java.util.List;

public record SupportUsersRequest(
        List<Long> userIds
) {
}
