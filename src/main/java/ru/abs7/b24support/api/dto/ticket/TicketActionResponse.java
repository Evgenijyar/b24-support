package ru.abs7.b24support.api.dto.ticket;

public record TicketActionResponse(boolean success, String message, SupportTicketResponse ticket) {
}
