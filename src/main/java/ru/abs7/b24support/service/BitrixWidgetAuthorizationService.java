package ru.abs7.b24support.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.BitrixUser;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.repo.BitrixUserRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import tools.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Map;

@Service
public class BitrixWidgetAuthorizationService {

    private final PortalInstallationRepository portalRepository;
    private final BitrixUserRepository userRepository;
    private final BitrixRestClient bitrixRestClient;

    public BitrixWidgetAuthorizationService(PortalInstallationRepository portalRepository,
                                            BitrixUserRepository userRepository,
                                            BitrixRestClient bitrixRestClient) {
        this.portalRepository = portalRepository;
        this.userRepository = userRepository;
        this.bitrixRestClient = bitrixRestClient;
    }

    @Transactional(readOnly = true)
    public AuthorizedSupportUser authorize(String domain, String memberId, String accessToken) {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Админский портал Bitrix24 не настроен"
                ));

        String requestedDomain = normalizeDomain(domain);
        String configuredDomain = normalizeDomain(admin.getDomain());
        if (!configuredDomain.equals(requestedDomain)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Виджет открыт не на админском портале");
        }

        if (notBlank(admin.getMemberId()) && notBlank(memberId)
                && !admin.getMemberId().trim().equals(memberId.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Идентификатор портала не совпадает");
        }

        if (!notBlank(accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitrix24 не передал OAuth-токен пользователя");
        }

        JsonNode root;
        try {
            root = bitrixRestClient.callOAuth(configuredDomain, "user.current", accessToken, Map.of());
        } catch (BitrixRestException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось подтвердить пользователя Bitrix24", e);
        }

        JsonNode result = root.path("result");
        String userId = firstText(result, "ID", "id");
        if (!notBlank(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitrix24 не вернул идентификатор пользователя");
        }

        String active = firstText(result, "ACTIVE", "active");
        if ("N".equalsIgnoreCase(active) || "false".equalsIgnoreCase(active)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь Bitrix24 неактивен");
        }

        BitrixUser localUser = userRepository
                .findByPortalInstallationIdAndBitrixUserId(admin.getId(), userId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Пользователь не загружен в список сотрудников техподдержки"
                ));

        if (!localUser.isActive() || !localUser.isSupportMember()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Закрывать и удалять обращения могут только выбранные специалисты техподдержки"
            );
        }

        String firstName = firstText(result, "NAME", "name");
        String lastName = firstText(result, "LAST_NAME", "lastName");
        String displayName = joinName(firstName, lastName);
        if (!notBlank(displayName)) {
            displayName = firstNonBlank(localUser.getDisplayName(), localUser.getEmail(), "Специалист техподдержки");
        }

        return new AuthorizedSupportUser(admin, userId.trim(), displayName.trim());
    }

    private String normalizeDomain(String value) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceFirst("^https?://", "").replaceAll("/+$", "");
        if (cleaned.isBlank() || !cleaned.matches("[a-z0-9.-]+(?::[0-9]{1,5})?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный домен Bitrix24");
        }
        return cleaned;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asString(null);
                if (notBlank(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String joinName(String firstName, String lastName) {
        String first = notBlank(firstName) ? firstName.trim() : "";
        String last = notBlank(lastName) ? lastName.trim() : "";
        return (first + " " + last).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record AuthorizedSupportUser(
            PortalInstallation adminPortal,
            String userId,
            String userName
    ) {
    }
}
