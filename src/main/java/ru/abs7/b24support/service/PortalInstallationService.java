package ru.abs7.b24support.service;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.PortalInstallationListResponse;
import ru.abs7.b24support.api.dto.PortalInstallationRequest;
import ru.abs7.b24support.api.dto.PortalInstallationResponse;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.PortalStatus;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PortalInstallationService {

    private final PortalInstallationRepository repository;

    public PortalInstallationService(PortalInstallationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PortalInstallationListResponse list() {
        List<PortalInstallationResponse> items = repository.findAll(Sort.by(
                        Sort.Order.asc("role"),
                        Sort.Order.asc("title"),
                        Sort.Order.asc("id")
                ))
                .stream()
                .map(PortalInstallationResponse::from)
                .toList();

        return new PortalInstallationListResponse(
                repository.count(),
                repository.countByRole(PortalRole.ADMIN),
                repository.countByRole(PortalRole.CLIENT),
                items
        );
    }

    @Transactional(readOnly = true)
    public PortalInstallationResponse get(Long id) {
        return PortalInstallationResponse.from(findRequired(id));
    }

    @Transactional
    public PortalInstallationResponse create(PortalInstallationRequest request) {
        PortalRole role = request.role();
        String domain = normalizeDomain(request.domain());
        String clientCode = normalizeClientCode(request.clientCode(), role);

        validateAdminUniqueness(null, role);
        validateDomainUniqueness(null, domain);
        validateClientCodeUniqueness(null, clientCode);

        PortalInstallation installation = new PortalInstallation(
                role,
                clientCode,
                cleanRequired(request.title()),
                domain
        );

        applyEditableFields(installation, request);
        installation.setStatus(request.status() == null ? PortalStatus.DRAFT : request.status());

        if (installation.getStatus() == PortalStatus.ACTIVE) {
            installation.setConnectedAt(OffsetDateTime.now());
        }

        return PortalInstallationResponse.from(repository.save(installation));
    }

    @Transactional
    public PortalInstallationResponse update(Long id, PortalInstallationRequest request) {
        PortalInstallation installation = findRequired(id);
        PortalRole role = request.role();
        String domain = normalizeDomain(request.domain());
        String clientCode = normalizeClientCode(request.clientCode(), role);

        validateAdminUniqueness(id, role);
        validateDomainUniqueness(id, domain);
        validateClientCodeUniqueness(id, clientCode);

        installation.setRole(role);
        installation.setClientCode(clientCode);
        installation.setTitle(cleanRequired(request.title()));
        installation.setDomain(domain);
        applyEditableFields(installation, request);

        if (request.status() != null) {
            PortalStatus previous = installation.getStatus();
            installation.setStatus(request.status());
            if (previous != PortalStatus.ACTIVE && request.status() == PortalStatus.ACTIVE) {
                installation.setConnectedAt(OffsetDateTime.now());
            }
        }

        installation.markUpdated();
        return PortalInstallationResponse.from(repository.save(installation));
    }

    @Transactional
    public void delete(Long id) {
        PortalInstallation installation = findRequired(id);
        repository.delete(installation);
    }

    private void applyEditableFields(PortalInstallation installation, PortalInstallationRequest request) {
        installation.setMemberId(cleanNullable(request.memberId()));
        installation.setWebhookUrl(cleanNullable(request.webhookUrl()));
        installation.setBotId(cleanNullable(request.botId()));
        installation.setSupportDialogId(cleanNullable(request.supportDialogId()));
    }

    private PortalInstallation findRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Портал не найден"
        ));
    }

    private void validateAdminUniqueness(Long currentId, PortalRole role) {
        if (role != PortalRole.ADMIN) {
            return;
        }

        boolean anotherAdminExists = repository.findAllByRoleOrderByTitleAsc(PortalRole.ADMIN)
                .stream()
                .anyMatch(item -> currentId == null || !item.getId().equals(currentId));

        if (anotherAdminExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Админский Bitrix24 уже добавлен");
        }
    }

    private void validateDomainUniqueness(Long currentId, String domain) {
        repository.findByDomain(domain)
                .filter(item -> currentId == null || !item.getId().equals(currentId))
                .ifPresent(item -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Портал с таким доменом уже добавлен");
                });
    }

    private void validateClientCodeUniqueness(Long currentId, String clientCode) {
        repository.findByClientCode(clientCode)
                .filter(item -> currentId == null || !item.getId().equals(currentId))
                .ifPresent(item -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Такой код клиента уже используется");
                });
    }

    private String normalizeClientCode(String value, PortalRole role) {
        String cleaned = cleanNullable(value);
        if (cleaned != null) {
            return cleaned.toLowerCase(Locale.ROOT);
        }

        if (role == PortalRole.ADMIN) {
            return "admin";
        }

        return "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String normalizeDomain(String value) {
        String cleaned = cleanRequired(value)
                .replace("https://", "")
                .replace("http://", "")
                .replaceAll("/+$", "")
                .toLowerCase(Locale.ROOT);

        if (cleaned.isBlank() || cleaned.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный домен Bitrix24");
        }

        return cleaned;
    }

    private String cleanRequired(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните обязательные поля");
        }
        return cleaned;
    }

    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
