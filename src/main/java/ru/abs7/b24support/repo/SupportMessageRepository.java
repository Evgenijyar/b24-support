package ru.abs7.b24support.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.abs7.b24support.domain.SupportMessage;
import java.util.List;
import java.util.Optional;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findTop50ByOrderByCreatedAtDesc();

    Optional<SupportMessage> findFirstByClientInstallation_IdAndClientMessageIdOrderByIdAsc(Long clientInstallationId,
                                                                                           String clientMessageId);

    Optional<SupportMessage> findFirstByDirectionAndAdminMessageIdOrderByIdAsc(String direction,
                                                                               String adminMessageId);

    Optional<SupportMessage> findFirstByClientInstallation_IdAndClientDialogIdIsNotNullOrderByCreatedAtDesc(Long clientInstallationId);
}

