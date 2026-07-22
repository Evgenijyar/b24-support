package ru.abs7.b24support.repo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.abs7.b24support.domain.CrmIntegrationConfig;
import java.util.Optional;

public interface CrmIntegrationConfigRepository extends JpaRepository<CrmIntegrationConfig, Long> {
    @EntityGraph(attributePaths = "adminPortal")
    Optional<CrmIntegrationConfig> findByAdminPortal_Id(Long adminPortalId);
}
