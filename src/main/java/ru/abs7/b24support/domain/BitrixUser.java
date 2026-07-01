package ru.abs7.b24support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "bitrix_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_bitrix_user_portal_user", columnNames = {"portal_installation_id", "bitrix_user_id"})
)
public class BitrixUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portal_installation_id", nullable = false)
    private PortalInstallation portalInstallation;

    @Column(name = "bitrix_user_id", nullable = false, length = 64)
    private String bitrixUserId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "support_member", nullable = false)
    private boolean supportMember;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(name = "second_name", length = 255)
    private String secondName;

    @Column(name = "display_name", length = 512)
    private String displayName;

    @Column(name = "email", length = 512)
    private String email;

    @Column(name = "work_position", length = 512)
    private String workPosition;

    @Column(name = "personal_photo", columnDefinition = "text")
    private String personalPhoto;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(name = "loaded_at", nullable = false)
    private OffsetDateTime loadedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected BitrixUser() {
    }

    public BitrixUser(PortalInstallation portalInstallation, String bitrixUserId) {
        this.portalInstallation = portalInstallation;
        this.bitrixUserId = bitrixUserId;
        this.active = true;
        this.supportMember = false;
        this.loadedAt = OffsetDateTime.now();
        this.createdAt = this.loadedAt;
        this.updatedAt = this.loadedAt;
    }

    public void markLoaded() {
        OffsetDateTime now = OffsetDateTime.now();
        this.loadedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public PortalInstallation getPortalInstallation() {
        return portalInstallation;
    }

    public void setPortalInstallation(PortalInstallation portalInstallation) {
        this.portalInstallation = portalInstallation;
    }

    public String getBitrixUserId() {
        return bitrixUserId;
    }

    public void setBitrixUserId(String bitrixUserId) {
        this.bitrixUserId = bitrixUserId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSupportMember() {
        return supportMember;
    }

    public void setSupportMember(boolean supportMember) {
        this.supportMember = supportMember;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getSecondName() {
        return secondName;
    }

    public void setSecondName(String secondName) {
        this.secondName = secondName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWorkPosition() {
        return workPosition;
    }

    public void setWorkPosition(String workPosition) {
        this.workPosition = workPosition;
    }

    public String getPersonalPhoto() {
        return personalPhoto;
    }

    public void setPersonalPhoto(String personalPhoto) {
        this.personalPhoto = personalPhoto;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public OffsetDateTime getLoadedAt() {
        return loadedAt;
    }

    public void setLoadedAt(OffsetDateTime loadedAt) {
        this.loadedAt = loadedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
