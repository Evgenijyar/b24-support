package ru.abs7.b24support.api.dto.widget;

import ru.abs7.b24support.api.dto.ticket.SupportTicketResponse;

public record WidgetContextResponse(
        boolean available,
        String action,
        String buttonLabel,
        String message,
        String currentUserId,
        String currentUserName,
        SupportTicketResponse ticket
) {
}
