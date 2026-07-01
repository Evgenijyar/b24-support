package ru.abs7.b24support.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.abs7.b24support.domain.BitrixUser;
import java.util.List;
import java.util.Optional;

public interface BitrixUserRepository extends JpaRepository<BitrixUser, Long> {

    List<BitrixUser> findAllByPortalInstallationIdOrderBySupportMemberDescLastNameAscFirstNameAscIdAsc(Long portalInstallationId);

    Optional<BitrixUser> findByPortalInstallationIdAndBitrixUserId(Long portalInstallationId, String bitrixUserId);

    long countByPortalInstallationId(Long portalInstallationId);

    long countByPortalInstallationIdAndSupportMemberTrue(Long portalInstallationId);
}
