package ru.abs7.b24support.api.dto.ticket;

import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.domain.SupportTicketStatus;
import java.time.OffsetDateTime;

public record SupportTicketResponse(
        Long id,
        Long clientPortalId,
        String clientCode,
        String clientTitle,
        SupportTicketStatus status,
        String clientDialogId,
        String adminChatId,
        String adminDialogId,
        String chatTitle,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime deleteAfter,
        OffsetDateTime deletedAt,
        String closedByUserId,
        String closedByUserName,
        int deletionAttempts,
        String lastError
) {
    public static SupportTicketResponse from(SupportTicket ticket) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getClientInstallation().getId(),
                ticket.getClientInstallation().getClientCode(),
                ticket.getClientInstallation().getTitle(),
                ticket.getStatus(),
                ticket.getClientDialogId(),
                ticket.getAdminChatId(),
                ticket.getAdminDialogId(),
                ticket.getChatTitle(),
                ticket.getOpenedAt(),
                ticket.getClosedAt(),
                ticket.getDeleteAfter(),
                ticket.getDeletedAt(),
                ticket.getClosedByUserId(),
                ticket.getClosedByUserName(),
                ticket.getDeletionAttempts(),
                ticket.getLastError()
        );
    }
}
