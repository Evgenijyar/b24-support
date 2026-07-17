package ru.abs7.b24support.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClosedChatCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ClosedChatCleanupScheduler.class);

    private final SupportTicketService supportTicketService;

    public ClosedChatCleanupScheduler(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @Scheduled(
            initialDelayString = "${app.closed-chat-cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${app.closed-chat-cleanup-interval-ms:3600000}"
    )
    public void deleteExpiredChats() {
        try {
            int deleted = supportTicketService.deleteExpiredChats();
            if (deleted > 0) {
                LOG.info("Automatically deleted {} closed support chats", deleted);
            }
        } catch (RuntimeException e) {
            LOG.error("Closed support chat cleanup failed", e);
        }
    }
}
