package ru.abs7.b24support.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.abs7.b24support.api.dto.ticket.SupportSettingsResponse;
import ru.abs7.b24support.domain.SupportSettings;
import ru.abs7.b24support.repo.SupportSettingsRepository;

@Service
public class SupportSettingsService {

    private final SupportSettingsRepository repository;

    public SupportSettingsService(SupportSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SupportSettingsResponse get() {
        return SupportSettingsResponse.from(load());
    }

    @Transactional
    public SupportSettingsResponse update(int retentionDays) {
        SupportSettings settings = load();
        settings.setClosedChatRetentionDays(retentionDays);
        return SupportSettingsResponse.from(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public int retentionDays() {
        return load().getClosedChatRetentionDays();
    }

    private SupportSettings load() {
        return repository.findById(SupportSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Строка support_settings не создана миграцией Flyway"));
    }
}
