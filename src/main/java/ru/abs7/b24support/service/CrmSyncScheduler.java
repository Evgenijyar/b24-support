package ru.abs7.b24support.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CrmSyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CrmSyncScheduler.class);
    private final CrmTicketSyncService crmTicketSyncService;

    public CrmSyncScheduler(CrmTicketSyncService crmTicketSyncService) {
        this.crmTicketSyncService = crmTicketSyncService;
    }

    @Scheduled(
            initialDelayString = "${app.crm-sync-initial-delay-ms:90000}",
            fixedDelayString = "${app.crm-sync-interval-ms:60000}"
    )
    public void retryPendingCrmOperations() {
        try {
            int processed = crmTicketSyncService.retryPending();
            if (processed > 0) {
                LOG.info("Processed {} pending CRM synchronization operations", processed);
            }
        } catch (RuntimeException e) {
            LOG.error("CRM synchronization retry failed", e);
        }
    }
}
