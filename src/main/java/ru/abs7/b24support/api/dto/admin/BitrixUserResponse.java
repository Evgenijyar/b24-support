package ru.abs7.b24support.api.dto.admin;

import ru.abs7.b24support.domain.BitrixUser;
import java.time.OffsetDateTime;

public record BitrixUserResponse(
        Long id,
        Long portalInstallationId,
        String bitrixUserId,
        boolean active,
        boolean supportMember,
        String firstName,
        String lastName,
        String secondName,
        String displayName,
        String email,
        String workPosition,
        String personalPhoto,
        OffsetDateTime loadedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BitrixUserResponse from(BitrixUser user) {
        return new BitrixUserResponse(
                user.getId(),
                user.getPortalInstallation().getId(),
                user.getBitrixUserId(),
                user.isActive(),
                user.isSupportMember(),
                user.getFirstName(),
                user.getLastName(),
                user.getSecondName(),
                user.getDisplayName(),
                user.getEmail(),
                user.getWorkPosition(),
                user.getPersonalPhoto(),
                user.getLoadedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
