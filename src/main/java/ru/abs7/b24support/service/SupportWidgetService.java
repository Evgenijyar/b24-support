package ru.abs7.b24support.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.ticket.SupportTicketResponse;
import ru.abs7.b24support.api.dto.ticket.TicketActionResponse;
import ru.abs7.b24support.api.dto.widget.WidgetActionRequest;
import ru.abs7.b24support.api.dto.widget.WidgetActionResponse;
import ru.abs7.b24support.api.dto.widget.WidgetActionType;
import ru.abs7.b24support.api.dto.widget.WidgetContextRequest;
import ru.abs7.b24support.api.dto.widget.WidgetContextResponse;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.domain.SupportTicketStatus;
import ru.abs7.b24support.repo.SupportTicketRepository;

@Service
public class SupportWidgetService {

    private final BitrixWidgetAuthorizationService authorizationService;
    private final SupportTicketRepository ticketRepository;
    private final SupportTicketService ticketService;

    public SupportWidgetService(BitrixWidgetAuthorizationService authorizationService,
                                SupportTicketRepository ticketRepository,
                                SupportTicketService ticketService) {
        this.authorizationService = authorizationService;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public WidgetContextResponse context(WidgetContextRequest request) {
        BitrixWidgetAuthorizationService.AuthorizedSupportUser user = authorizationService.authorize(
                request.domain(),
                request.memberId(),
                request.accessToken()
        );

        SupportTicket ticket = ticketRepository.findFirstByAdminDialogIdOrderByIdDesc(cleanDialogId(request.dialogId()))
                .orElse(null);
        if (ticket == null) {
            return new WidgetContextResponse(
                    false,
                    "NONE",
                    null,
                    "Этот чат не связан с обращением техподдержки",
                    user.userId(),
                    user.userName(),
                    null
            );
        }

        return responseFor(ticket, user.userId(), user.userName());
    }

    public WidgetActionResponse execute(WidgetActionRequest request) {
        BitrixWidgetAuthorizationService.AuthorizedSupportUser user = authorizationService.authorize(
                request.domain(),
                request.memberId(),
                request.accessToken()
        );

        String dialogId = cleanDialogId(request.dialogId());
        SupportTicket ticket = ticketRepository.findFirstByAdminDialogIdOrderByIdDesc(dialogId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Этот чат не связан с обращением техподдержки"
                ));

        TicketActionResponse result;
        if (request.action() == WidgetActionType.CLOSE) {
            if (ticket.getStatus() != SupportTicketStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Обращение уже не находится в работе");
            }
            result = ticketService.closeByDialogId(dialogId, user.userId(), user.userName());
        } else if (request.action() == WidgetActionType.DELETE) {
            if (ticket.getStatus() != SupportTicketStatus.CLOSED
                    && ticket.getStatus() != SupportTicketStatus.DELETING
                    && ticket.getStatus() != SupportTicketStatus.DELETED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Сначала закройте обращение");
            }
            result = ticketService.deleteChatByDialogId(dialogId);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное действие виджета");
        }

        SupportTicketResponse updatedTicket = result.ticket();
        ActionState next = actionState(updatedTicket.status());
        return new WidgetActionResponse(
                result.success(),
                result.message(),
                next.action(),
                next.buttonLabel(),
                updatedTicket
        );
    }

    private WidgetContextResponse responseFor(SupportTicket ticket, String userId, String userName) {
        SupportTicketResponse ticketResponse = SupportTicketResponse.from(ticket);
        ActionState state = actionState(ticket.getStatus());
        return new WidgetContextResponse(
                state.available(),
                state.action(),
                state.buttonLabel(),
                state.message(),
                userId,
                userName,
                ticketResponse
        );
    }

    private ActionState actionState(SupportTicketStatus status) {
        return switch (status) {
            case OPEN -> new ActionState(true, "CLOSE", "Обращение закрыто", "Обращение находится в работе");
            case CLOSED -> new ActionState(true, "DELETE", "Удалить чат", "Обращение закрыто. Чат можно удалить вручную");
            case DELETING -> new ActionState(false, "NONE", null, "Чат удаляется");
            case DELETED -> new ActionState(false, "NONE", null, "Чат удалён");
            case OPENING -> new ActionState(false, "NONE", null, "Чат обращения ещё создаётся");
            case ERROR -> new ActionState(false, "NONE", null, "Обращение находится в состоянии ошибки");
        };
    }

    private String cleanDialogId(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank() || cleaned.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный dialogId чата");
        }
        return cleaned;
    }

    private record ActionState(boolean available, String action, String buttonLabel, String message) {
    }
}
