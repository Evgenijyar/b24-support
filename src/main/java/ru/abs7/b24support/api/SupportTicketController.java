package ru.abs7.b24support.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.abs7.b24support.api.dto.ticket.CloseTicketRequest;
import ru.abs7.b24support.api.dto.ticket.SupportSettingsRequest;
import ru.abs7.b24support.api.dto.ticket.SupportSettingsResponse;
import ru.abs7.b24support.api.dto.ticket.SupportTicketListResponse;
import ru.abs7.b24support.api.dto.ticket.SupportTicketResponse;
import ru.abs7.b24support.api.dto.ticket.TicketActionResponse;
import ru.abs7.b24support.service.SupportActionGuard;
import ru.abs7.b24support.service.SupportSettingsService;
import ru.abs7.b24support.service.SupportTicketService;

@RestController
@RequestMapping("/api/support")
public class SupportTicketController {

    private static final String ACTION_KEY_HEADER = "X-B24-Support-Key";

    private final SupportTicketService ticketService;
    private final SupportSettingsService settingsService;
    private final SupportActionGuard actionGuard;

    public SupportTicketController(SupportTicketService ticketService,
                                   SupportSettingsService settingsService,
                                   SupportActionGuard actionGuard) {
        this.ticketService = ticketService;
        this.settingsService = settingsService;
        this.actionGuard = actionGuard;
    }

    @GetMapping("/tickets")
    public SupportTicketListResponse recentTickets() {
        return ticketService.recent();
    }

    @GetMapping("/tickets/{ticketId}")
    public SupportTicketResponse ticket(@PathVariable Long ticketId) {
        return ticketService.get(ticketId);
    }

    @GetMapping("/tickets/by-dialog/{dialogId}")
    public SupportTicketResponse ticketByDialog(@PathVariable String dialogId) {
        return ticketService.getByDialogId(dialogId);
    }

    @PostMapping("/tickets/{ticketId}/close")
    public TicketActionResponse close(@PathVariable Long ticketId,
                                      @RequestHeader(name = ACTION_KEY_HEADER, required = false) String actionKey,
                                      @RequestBody(required = false) CloseTicketRequest request) {
        actionGuard.requireValidKey(actionKey);
        return ticketService.close(
                ticketId,
                request == null ? null : request.userId(),
                request == null ? null : request.userName()
        );
    }

    @PostMapping("/tickets/by-dialog/{dialogId}/close")
    public TicketActionResponse closeByDialog(@PathVariable String dialogId,
                                              @RequestHeader(name = ACTION_KEY_HEADER, required = false) String actionKey,
                                              @RequestBody(required = false) CloseTicketRequest request) {
        actionGuard.requireValidKey(actionKey);
        return ticketService.closeByDialogId(
                dialogId,
                request == null ? null : request.userId(),
                request == null ? null : request.userName()
        );
    }

    @PostMapping("/tickets/{ticketId}/delete-chat")
    public TicketActionResponse deleteChat(@PathVariable Long ticketId,
                                           @RequestHeader(name = ACTION_KEY_HEADER, required = false) String actionKey) {
        actionGuard.requireValidKey(actionKey);
        return ticketService.deleteChat(ticketId);
    }

    @PostMapping("/tickets/by-dialog/{dialogId}/delete-chat")
    public TicketActionResponse deleteChatByDialog(@PathVariable String dialogId,
                                                   @RequestHeader(name = ACTION_KEY_HEADER, required = false) String actionKey) {
        actionGuard.requireValidKey(actionKey);
        return ticketService.deleteChatByDialogId(dialogId);
    }

    @GetMapping("/settings")
    public SupportSettingsResponse settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    public SupportSettingsResponse updateSettings(
            @RequestHeader(name = ACTION_KEY_HEADER, required = false) String actionKey,
            @Valid @RequestBody SupportSettingsRequest request) {
        actionGuard.requireValidKey(actionKey);
        return settingsService.update(request.closedChatRetentionDays());
    }
}
