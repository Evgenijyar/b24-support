package ru.abs7.b24support.api.dto.crm;

public record CrmStageOption(
        String id,
        String name,
        Integer sort,
        String semantics
) {
}
