package ru.abs7.b24support.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.abs7.b24support.domain.SupportSettings;

public interface SupportSettingsRepository extends JpaRepository<SupportSettings, Long> {
}
