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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SupportTicket() {
    }

    public SupportTicket(PortalInstallation clientInstallation, String clientDialogId, String chatTitle) {
        OffsetDateTime now = OffsetDateTime.now();
        this.clientInstallation = clientInstallation;
        this.clientDialogId = clientDialogId;
        this.chatTitle = chatTitle;
        this.status = SupportTicketStatus.OPENING;
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

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public PortalInstallation getClientInstallation() { return clientInstallation; }
    public SupportTicketStatus getStatus() { return status; }
    public String getClientDialogId() { return clientDialogId; }
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
