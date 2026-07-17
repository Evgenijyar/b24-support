package ru.abs7.b24support.api.dto.ticket;

import java.util.List;

public record SupportTicketListResponse(int total, List<SupportTicketResponse> items) {
}
