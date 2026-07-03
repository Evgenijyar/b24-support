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
import java.time.OffsetDateTime;

@Entity
@Table(name = "support_message")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String direction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_installation_id")
    private PortalInstallation clientInstallation;

    @Column(name = "client_dialog_id", length = 255)
    private String clientDialogId;

    @Column(name = "client_message_id", length = 255)
    private String clientMessageId;

    @Column(name = "admin_dialog_id", length = 255)
    private String adminDialogId;

    @Column(name = "admin_message_id", length = 255)
    private String adminMessageId;

    @Column(name = "reply_to_admin_message_id", length = 255)
    private String replyToAdminMessageId;

    @Column(name = "sender_user_id", length = 255)
    private String senderUserId;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(columnDefinition = "text")
    private String text;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "raw_event_json", columnDefinition = "text")
    private String rawEventJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SupportMessage() {
    }

    public SupportMessage(PortalInstallation clientInstallation) {
        this.clientInstallation = clientInstallation;
        this.direction = "CLIENT_TO_ADMIN";
        this.status = "NEW";
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public PortalInstallation getClientInstallation() {
        return clientInstallation;
    }

    public void setClientInstallation(PortalInstallation clientInstallation) {
        this.clientInstallation = clientInstallation;
    }

    public String getClientDialogId() {
        return clientDialogId;
    }

    public void setClientDialogId(String clientDialogId) {
        this.clientDialogId = clientDialogId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getAdminDialogId() {
        return adminDialogId;
    }

    public void setAdminDialogId(String adminDialogId) {
        this.adminDialogId = adminDialogId;
    }

    public String getAdminMessageId() {
        return adminMessageId;
    }

    public void setAdminMessageId(String adminMessageId) {
        this.adminMessageId = adminMessageId;
    }

    public String getReplyToAdminMessageId() {
        return replyToAdminMessageId;
    }

    public void setReplyToAdminMessageId(String replyToAdminMessageId) {
        this.replyToAdminMessageId = replyToAdminMessageId;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawEventJson() {
        return rawEventJson;
    }

    public void setRawEventJson(String rawEventJson) {
        this.rawEventJson = rawEventJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
