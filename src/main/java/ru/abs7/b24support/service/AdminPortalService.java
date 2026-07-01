package ru.abs7.b24support.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.abs7.b24support.api.dto.PortalInstallationResponse;
import ru.abs7.b24support.api.dto.admin.AdminPortalActionResponse;
import ru.abs7.b24support.api.dto.admin.AdminPortalSummaryResponse;
import ru.abs7.b24support.api.dto.admin.BitrixUserListResponse;
import ru.abs7.b24support.api.dto.admin.BitrixUserResponse;
import ru.abs7.b24support.bitrix.BitrixRestClient;
import ru.abs7.b24support.bitrix.BitrixRestException;
import ru.abs7.b24support.domain.BitrixUser;
import ru.abs7.b24support.domain.PortalInstallation;
import ru.abs7.b24support.domain.PortalRole;
import ru.abs7.b24support.domain.PortalStatus;
import ru.abs7.b24support.repo.BitrixUserRepository;
import ru.abs7.b24support.repo.PortalInstallationRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminPortalService {

    private static final int MAX_USER_GET_PAGES = 50;

    private final PortalInstallationRepository portalRepository;
    private final BitrixUserRepository bitrixUserRepository;
    private final BitrixRestClient bitrixRestClient;
    private final ObjectMapper objectMapper;

    public AdminPortalService(PortalInstallationRepository portalRepository,
                              BitrixUserRepository bitrixUserRepository,
                              BitrixRestClient bitrixRestClient,
                              ObjectMapper objectMapper) {
        this.portalRepository = portalRepository;
        this.bitrixUserRepository = bitrixUserRepository;
        this.bitrixRestClient = bitrixRestClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminPortalSummaryResponse summary() {
        PortalInstallation admin = portalRepository.findFirstByRoleOrderByIdAsc(PortalRole.ADMIN).orElse(null);
        if (admin == null) {
            return new AdminPortalSummaryResponse(null, 0, 0);
        }

        return new AdminPortalSummaryResponse(
                PortalInstallationResponse.from(admin),
                bitrixUserRepository.countByPortalInstallationId(admin.getId()),
                bitrixUserRepository.countByPortalInstallationIdAndSupportMemberTrue(admin.getId())
        );
    }

    @Transactional(readOnly = true)
    public BitrixUserListResponse users(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        List<BitrixUserResponse> users = bitrixUserRepository
                .findAllByPortalInstallationIdOrderBySupportMemberDescLastNameAscFirstNameAscIdAsc(admin.getId())
                .stream()
                .map(BitrixUserResponse::from)
                .toList();

        return new BitrixUserListResponse(
                admin.getId(),
                users.size(),
                users.stream().filter(BitrixUserResponse::supportMember).count(),
                users
        );
    }

    @Transactional
    public AdminPortalActionResponse testConnection(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);

        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("start", "0");
            bitrixRestClient.call(admin.getWebhookUrl(), "user.get", params);

            admin.setStatus(PortalStatus.ACTIVE);
            admin.setConnectedAt(OffsetDateTime.now());
            admin.setLastError(null);
            admin.markUpdated();
            portalRepository.save(admin);

            return response(true, "Подключение к Bitrix24 работает", admin);
        } catch (BitrixRestException e) {
            admin.setStatus(PortalStatus.ERROR);
            admin.setLastError(e.getMessage());
            admin.markUpdated();
            portalRepository.save(admin);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public AdminPortalActionResponse loadUsers(Long portalId) {
        PortalInstallation admin = findAdminPortal(portalId);
        requireWebhook(admin);

        try {
            List<JsonNode> remoteUsers = fetchAllUsers(admin.getWebhookUrl());
            for (JsonNode remoteUser : remoteUsers) {
                upsertUser(admin, remoteUser);
            }

            admin.setStatus(PortalStatus.ACTIVE);
            admin.setConnectedAt(OffsetDateTime.now());
            admin.setLastError(null);
            admin.markUpdated();
            portalRepository.save(admin);

            return response(true, "Сотрудники загружены: " + remoteUsers.size(), admin);
        } catch (BitrixRestException e) {
            admin.setStatus(PortalStatus.ERROR);
            admin.setLastError(e.getMessage());
            admin.markUpdated();
            portalRepository.save(admin);
            return response(false, e.getMessage(), admin);
        }
    }

    @Transactional
    public BitrixUserListResponse saveSupportUsers(Long portalId, List<Long> userIds) {
        PortalInstallation admin = findAdminPortal(portalId);
        Set<Long> selectedIds = (userIds == null ? List.<Long>of() : userIds)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<BitrixUser> users = bitrixUserRepository
                .findAllByPortalInstallationIdOrderBySupportMemberDescLastNameAscFirstNameAscIdAsc(admin.getId());

        OffsetDateTime now = OffsetDateTime.now();
        for (BitrixUser user : users) {
            user.setSupportMember(selectedIds.contains(user.getId()));
            user.setUpdatedAt(now);
        }
        bitrixUserRepository.saveAll(users);

        return users(admin.getId());
    }

    private List<JsonNode> fetchAllUsers(String webhookUrl) {
        List<JsonNode> users = new ArrayList<>();
        int start = 0;

        for (int page = 0; page < MAX_USER_GET_PAGES; page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("start", String.valueOf(start));
            params.put("ORDER[LAST_NAME]", "ASC");
            params.put("ORDER[NAME]", "ASC");

            JsonNode root = bitrixRestClient.call(webhookUrl, "user.get", params);
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                throw new BitrixRestException("Bitrix24 вернул неожиданный формат user.get");
            }

            result.forEach(users::add);

            if (root.has("next") && root.path("next").canConvertToInt()) {
                start = root.path("next").asInt();
            } else {
                break;
            }
        }

        return users;
    }

    private void upsertUser(PortalInstallation admin, JsonNode remoteUser) {
        String bitrixUserId = text(remoteUser, "ID");
        if (bitrixUserId == null || bitrixUserId.isBlank()) {
            return;
        }

        BitrixUser user = bitrixUserRepository
                .findByPortalInstallationIdAndBitrixUserId(admin.getId(), bitrixUserId)
                .orElseGet(() -> new BitrixUser(admin, bitrixUserId));

        user.setActive(!"N".equalsIgnoreCase(text(remoteUser, "ACTIVE")));
        user.setFirstName(text(remoteUser, "NAME"));
        user.setLastName(text(remoteUser, "LAST_NAME"));
        user.setSecondName(text(remoteUser, "SECOND_NAME"));
        user.setEmail(text(remoteUser, "EMAIL"));
        user.setWorkPosition(text(remoteUser, "WORK_POSITION"));
        user.setPersonalPhoto(text(remoteUser, "PERSONAL_PHOTO"));
        user.setDisplayName(buildDisplayName(user));
        user.setRawJson(toJson(remoteUser));
        user.markLoaded();

        bitrixUserRepository.save(user);
    }

    private String buildDisplayName(BitrixUser user) {
        String joined = List.of(
                        valueOrEmpty(user.getLastName()),
                        valueOrEmpty(user.getFirstName()),
                        valueOrEmpty(user.getSecondName())
                )
                .stream()
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));

        if (!joined.isBlank()) {
            return joined;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "Пользователь " + user.getBitrixUserId();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.toString();
        }
    }

    private void requireWebhook(PortalInstallation admin) {
        if (admin.getWebhookUrl() == null || admin.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У админского портала не заполнен Webhook / REST URL");
        }
    }

    private PortalInstallation findAdminPortal(Long portalId) {
        PortalInstallation portal = portalRepository.findById(portalId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Портал не найден"
        ));

        if (portal.getRole() != PortalRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Операция доступна только для админского портала");
        }

        return portal;
    }

    private AdminPortalActionResponse response(boolean success, String message, PortalInstallation admin) {
        return new AdminPortalActionResponse(
                success,
                message,
                PortalInstallationResponse.from(admin),
                bitrixUserRepository.countByPortalInstallationId(admin.getId()),
                bitrixUserRepository.countByPortalInstallationIdAndSupportMemberTrue(admin.getId())
        );
    }
}
