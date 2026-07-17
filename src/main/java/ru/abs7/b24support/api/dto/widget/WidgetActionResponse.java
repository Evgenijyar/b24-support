package ru.abs7.b24support.api.dto.widget;

import ru.abs7.b24support.api.dto.ticket.SupportTicketResponse;

public record WidgetActionResponse(
        boolean success,
        String message,
        String nextAction,
        String nextButtonLabel,
        SupportTicketResponse ticket
) {
}
