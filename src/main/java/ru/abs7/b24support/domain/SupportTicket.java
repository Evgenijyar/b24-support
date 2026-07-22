package ru.abs7.b24support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "support_ticket")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_installation_id", nullable = false)
    private PortalInstallation clientInstallation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupportTicketStatus status;

    @Column(name = "client_dialog_id", nullable = false, length = 255)
    private String clientDialogId;

    @Column(name = "client_sequence_number", nullable = false)
    private Long clientSequenceNumber;

    @Column(name = "admin_chat_id", length = 64)
    private String adminChatId;

    @Column(name = "admin_dialog_id", length = 255)
    private String adminDialogId;

    @Column(name = "chat_title", nullable = false, length = 255)
    private String chatTitle;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "delete_after")
    private OffsetDateTime deleteAfter;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "closed_by_user_id", length = 255)
    private String closedByUserId;

    @Column(name = "closed_by_user_name", length = 255)
    private String closedByUserName;

    @Column(name = "deletion_attempts", nullable = false)
    private int deletionAttempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "crm_item_id")
    private Long crmItemId;

    @Column(name = "crm_entity_type_id")
    private Integer crmEntityTypeId;

    @Column(name = "crm_category_id")
    private Integer crmCategoryId;

    @Column(name = "crm_company_id")
    private Long crmCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "crm_company_match_status", length = 32)
    private CrmCompanyMatchStatus crmCompanyMatchStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "crm_sync_status", nullable = false, length = 32)
    private CrmSyncStatus crmSyncStatus;

    @Column(name = "crm_last_error", columnDefinition = "text")
    private String crmLastError;

    @Column(name = "crm_created_at")
    private OffsetDateTime crmCreatedAt;

    @Column(name = "crm_closed_at")
    private OffsetDateTime crmClosedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SupportTicket() {
    }

    public SupportTicket(PortalInstallation clientInstallation, String clientDialogId, String chatTitle, Long clientSequenceNumber) {
        OffsetDateTime now = OffsetDateTime.now();
        this.clientInstallation = clientInstallation;
        this.clientDialogId = clientDialogId;
        this.clientSequenceNumber = clientSequenceNumber;
        this.chatTitle = chatTitle;
        this.status = SupportTicketStatus.OPENING;
        this.crmSyncStatus = CrmSyncStatus.PENDING;
        this.crmCompanyMatchStatus = CrmCompanyMatchStatus.NOT_ATTEMPTED;
        this.openedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markOpen(String adminChatId, String adminDialogId) {
        this.adminChatId = adminChatId;
        this.adminDialogId = adminDialogId;
        this.status = SupportTicketStatus.OPEN;
        this.lastError = null;
        touch();
    }

    public void markClosed(String userId, String userName, OffsetDateTime deleteAfter) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = SupportTicketStatus.CLOSED;
        this.closedAt = now;
        this.closedByUserId = userId;
        this.closedByUserName = userName;
        this.deleteAfter = deleteAfter;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markDeleting() {
        this.status = SupportTicketStatus.DELETING;
        this.deletionAttempts++;
        this.lastError = null;
        touch();
    }

    public void markDeletionFailed(String error) {
        this.status = SupportTicketStatus.CLOSED;
        this.lastError = error;
        touch();
    }

    public void markDeleted() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = SupportTicketStatus.DELETED;
        this.deletedAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markError(String error) {
        this.status = SupportTicketStatus.ERROR;
        this.lastError = error;
        touch();
    }

    public void updateClientDialogId(String clientDialogId) {
        if (clientDialogId != null && !clientDialogId.isBlank()) {
            this.clientDialogId = clientDialogId;
            touch();
        }
    }


    public void updateChatTitle(String chatTitle) {
        this.chatTitle = chatTitle;
        touch();
    }

    public void markCrmNotConfigured() {
        this.crmSyncStatus = CrmSyncStatus.NOT_CONFIGURED;
        this.crmLastError = null;
        touch();
    }

    public void markCrmPending(String error) {
        this.crmSyncStatus = CrmSyncStatus.PENDING;
        this.crmLastError = error;
        touch();
    }

    public void markCrmCreated(Long crmItemId,
                               Integer entityTypeId,
                               Integer categoryId,
                               Long companyId,
                               CrmCompanyMatchStatus companyMatchStatus) {
        this.crmItemId = crmItemId;
        this.crmEntityTypeId = entityTypeId;
        this.crmCategoryId = categoryId;
        this.crmCompanyId = companyId;
        this.crmCompanyMatchStatus = companyMatchStatus;
        this.crmSyncStatus = CrmSyncStatus.SYNCED;
        this.crmLastError = null;
        this.crmCreatedAt = OffsetDateTime.now();
        touch();
    }

    public void markCrmError(String error, CrmCompanyMatchStatus companyMatchStatus) {
        this.crmSyncStatus = CrmSyncStatus.ERROR;
        this.crmLastError = error;
        if (companyMatchStatus != null) {
            this.crmCompanyMatchStatus = companyMatchStatus;
        }
        touch();
    }

    public void markCrmClosed() {
        this.crmClosedAt = OffsetDateTime.now();
        this.crmLastError = null;
        this.crmSyncStatus = CrmSyncStatus.SYNCED;
        touch();
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public PortalInstallation getClientInstallation() { return clientInstallation; }
    public SupportTicketStatus getStatus() { return status; }
    public String getClientDialogId() { return clientDialogId; }
    public Long getClientSequenceNumber() { return clientSequenceNumber; }
    public String getAdminChatId() { return adminChatId; }
    public String getAdminDialogId() { return adminDialogId; }
    public String getChatTitle() { return chatTitle; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public OffsetDateTime getDeleteAfter() { return deleteAfter; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public String getClosedByUserId() { return closedByUserId; }
    public String getClosedByUserName() { return closedByUserName; }
    public int getDeletionAttempts() { return deletionAttempts; }
    public String getLastError() { return lastError; }
    public Long getCrmItemId() { return crmItemId; }
    public Integer getCrmEntityTypeId() { return crmEntityTypeId; }
    public Integer getCrmCategoryId() { return crmCategoryId; }
    public Long getCrmCompanyId() { return crmCompanyId; }
    public CrmCompanyMatchStatus getCrmCompanyMatchStatus() { return crmCompanyMatchStatus; }
    public CrmSyncStatus getCrmSyncStatus() { return crmSyncStatus; }
    public String getCrmLastError() { return crmLastError; }
    public OffsetDateTime getCrmCreatedAt() { return crmCreatedAt; }
    public OffsetDateTime getCrmClosedAt() { return crmClosedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
