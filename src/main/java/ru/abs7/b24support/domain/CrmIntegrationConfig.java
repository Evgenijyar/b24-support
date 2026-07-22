package ru.abs7.b24support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "crm_integration_config")
public class CrmIntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_portal_id", nullable = false, unique = true)
    private PortalInstallation adminPortal;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "entity_type_id", nullable = false)
    private Integer entityTypeId;

    @Column(name = "process_title", nullable = false, length = 255)
    private String processTitle;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "category_title", nullable = false, length = 255)
    private String categoryTitle;

    @Column(name = "open_stage_id", nullable = false, length = 255)
    private String openStageId;

    @Column(name = "open_stage_title", nullable = false, length = 255)
    private String openStageTitle;

    @Column(name = "closed_stage_id", nullable = false, length = 255)
    private String closedStageId;

    @Column(name = "closed_stage_title", nullable = false, length = 255)
    private String closedStageTitle;

    @Column(name = "responsible_user_id", nullable = false, length = 64)
    private String responsibleUserId;

    @Column(name = "responsible_user_name", nullable = false, length = 512)
    private String responsibleUserName;

    @Column(name = "configured_at", nullable = false)
    private OffsetDateTime configuredAt;

    @Column(name = "last_validated_at")
    private OffsetDateTime lastValidatedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CrmIntegrationConfig() {
    }

    public CrmIntegrationConfig(PortalInstallation adminPortal) {
        OffsetDateTime now = OffsetDateTime.now();
        this.adminPortal = adminPortal;
        this.enabled = true;
        this.configuredAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void configure(Integer entityTypeId,
                          String processTitle,
                          Integer categoryId,
                          String categoryTitle,
                          String openStageId,
                          String openStageTitle,
                          String closedStageId,
                          String closedStageTitle,
                          String responsibleUserId,
                          String responsibleUserName) {
        OffsetDateTime now = OffsetDateTime.now();
        this.entityTypeId = entityTypeId;
        this.processTitle = processTitle;
        this.categoryId = categoryId;
        this.categoryTitle = categoryTitle;
        this.openStageId = openStageId;
        this.openStageTitle = openStageTitle;
        this.closedStageId = closedStageId;
        this.closedStageTitle = closedStageTitle;
        this.responsibleUserId = responsibleUserId;
        this.responsibleUserName = responsibleUserName;
        this.enabled = true;
        this.configuredAt = now;
        this.lastValidatedAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markValid() {
        this.lastValidatedAt = OffsetDateTime.now();
        this.lastError = null;
        this.updatedAt = this.lastValidatedAt;
    }

    public void markInvalid(String error) {
        this.lastValidatedAt = OffsetDateTime.now();
        this.lastError = error;
        this.updatedAt = this.lastValidatedAt;
    }

    public Long getId() { return id; }
    public PortalInstallation getAdminPortal() { return adminPortal; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.updatedAt = OffsetDateTime.now(); }
    public Integer getEntityTypeId() { return entityTypeId; }
    public String getProcessTitle() { return processTitle; }
    public Integer getCategoryId() { return categoryId; }
    public String getCategoryTitle() { return categoryTitle; }
    public String getOpenStageId() { return openStageId; }
    public String getOpenStageTitle() { return openStageTitle; }
    public String getClosedStageId() { return closedStageId; }
    public String getClosedStageTitle() { return closedStageTitle; }
    public String getResponsibleUserId() { return responsibleUserId; }
    public String getResponsibleUserName() { return responsibleUserName; }
    public OffsetDateTime getConfiguredAt() { return configuredAt; }
    public OffsetDateTime getLastValidatedAt() { return lastValidatedAt; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
