package ru.abs7.b24support.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.conversation.SupportConversationListResponse;
import ru.abs7.b24support.api.dto.conversation.SupportConversationMessageListResponse;
import ru.abs7.b24support.api.dto.conversation.SupportConversationMessageResponse;
import ru.abs7.b24support.api.dto.conversation.SupportConversationSummaryResponse;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.SupportMessage;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import ru.abs7.b24support.repo.SupportMessageRepository;
import ru.abs7.b24support.repo.SupportTicketRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupportConversationService {

    private static final String CLIENT_DIRECTION = "CLIENT_TO_ADMIN";

    private final PortalInstallationRepository portalRepository;
    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;

    public SupportConversationService(PortalInstallationRepository portalRepository,
                                      SupportTicketRepository ticketRepository,
                                      SupportMessageRepository messageRepository) {
        this.portalRepository = portalRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public SupportConversationListResponse list(Long portalId) {
        PortalInstallation portal = portalRepository.findById(portalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Портал не найден"));

        List<SupportTicket> tickets = portal.getRole() == PortalRole.ADMIN
                ? ticketRepository.findTop100ByOrderByOpenedAtDesc()
                : ticketRepository.findTop100ByClientInstallation_IdOrderByOpenedAtDesc(portal.getId());

        if (tickets.isEmpty()) {
            return new SupportConversationListResponse(0, List.of());
        }

        List<Long> ticketIds = tickets.stream().map(SupportTicket::getId).toList();
        Map<Long, List<SupportMessage>> messagesByTicket = new LinkedHashMap<>();
        for (SupportMessage message : messageRepository.findAllBySupportTicket_IdInOrderByCreatedAtAsc(ticketIds)) {
            if (message.getSupportTicket() == null) {
                continue;
            }
            messagesByTicket.computeIfAbsent(message.getSupportTicket().getId(), ignored -> new ArrayList<>())
                    .add(message);
        }

        List<SupportConversationSummaryResponse> items = tickets.stream()
                .map(ticket -> toSummary(ticket, messagesByTicket.getOrDefault(ticket.getId(), List.of())))
                .toList();
        return new SupportConversationListResponse(items.size(), items);
    }

    @Transactional(readOnly = true)
    public SupportConversationMessageListResponse messages(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Обращение не найдено");
        }
        List<SupportConversationMessageResponse> items = messageRepository
                .findAllBySupportTicket_IdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(SupportConversationMessageResponse::from)
                .toList();
        return new SupportConversationMessageListResponse(ticketId, items.size(), items);
    }

    private SupportConversationSummaryResponse toSummary(SupportTicket ticket, Collection<SupportMessage> sourceMessages) {
        List<SupportMessage> messages = sourceMessages.stream()
                .sorted(Comparator.comparing(SupportMessage::getCreatedAt))
                .toList();

        SupportMessage firstClientMessage = messages.stream()
                .filter(message -> CLIENT_DIRECTION.equals(message.getDirection()))
                .findFirst()
                .orElse(null);
        SupportMessage lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);

        String requesterName = firstClientMessage == null || isBlank(firstClientMessage.getSenderName())
                ? "Клиент"
                : firstClientMessage.getSenderName().trim();
        String preview = lastMessage == null ? "Сообщений пока нет" : preview(lastMessage.getText());

        return new SupportConversationSummaryResponse(
                ticket.getId(),
                ticket.getClientInstallation().getId(),
                ticket.getClientInstallation().getTitle(),
                ticket.getClientSequenceNumber(),
                ticket.getStatus().name(),
                requesterName,
                ticket.getOpenedAt(),
                ticket.getClosedAt(),
                lastMessage == null ? ticket.getOpenedAt() : lastMessage.getCreatedAt(),
                preview,
                messages.size()
        );
    }

    private String preview(String text) {
        if (isBlank(text)) {
            return "Пустое сообщение";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 117) + "…";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
