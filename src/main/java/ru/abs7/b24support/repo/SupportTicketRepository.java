package ru.abs7.b24support.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.abs7.b24support.domain.CrmSyncStatus;
import ru.abs7.b24support.domain.SupportTicket;
import ru.abs7.b24support.domain.SupportTicketStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findFirstByClientInstallation_IdAndStatusInOrderByIdDesc(
            Long clientInstallationId,
            Collection<SupportTicketStatus> statuses
    );

    Optional<SupportTicket> findFirstByAdminDialogIdOrderByIdDesc(String adminDialogId);

    Optional<SupportTicket> findFirstByAdminChatIdOrderByIdDesc(String adminChatId);

    Optional<SupportTicket> findFirstByClientInstallation_IdOrderByClientSequenceNumberDesc(Long clientInstallationId);

    List<SupportTicket> findTop100ByOrderByOpenedAtDesc();

    List<SupportTicket> findTop50ByCrmSyncStatusInOrderByIdAsc(Collection<CrmSyncStatus> statuses);

    List<SupportTicket> findAllByStatusAndDeleteAfterLessThanEqualOrderByDeleteAfterAsc(
            SupportTicketStatus status,
            OffsetDateTime deleteAfter
    );
}
