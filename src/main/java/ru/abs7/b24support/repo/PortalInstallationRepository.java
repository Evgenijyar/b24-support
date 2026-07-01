package ru.abs7.b24support.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import java.util.List;
import java.util.Optional;

public interface PortalInstallationRepository extends JpaRepository<PortalInstallation, Long> {

    List<PortalInstallation> findAllByRoleOrderByTitleAsc(PortalRole role);

    Optional<PortalInstallation> findByDomain(String domain);

    Optional<PortalInstallation> findByClientCode(String clientCode);

    Optional<PortalInstallation> findFirstByRoleOrderByIdAsc(PortalRole role);

    long countByRole(PortalRole role);
}
