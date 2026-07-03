package ru.abs7.b24support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "portal_installation")
public class PortalInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PortalRole role;

    @Column(name = "client_code", nullable = false, unique = true, length = 64)
    private String clientCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, unique = true, length = 255)
    private String domain;

    @Column(name = "member_id", length = 255)
    private String memberId;

    @Column(name = "webhook_url", columnDefinition = "text")
    private String webhookUrl;

    @Column(name = "bot_id", length = 255)
    private String botId;

    @Column(name = "bot_code", length = 128)
    private String botCode;

    @Column(name = "bot_token", length = 64)
    private String botToken;

    @Column(name = "bot_type", length = 32)
    private String botType;

    @Column(name = "support_chat_id", length = 64)
    private String supportChatId;

    @Column(name = "support_dialog_id", length = 255)
    private String supportDialogId;

    @Column(name = "bot_registered_at")
    private OffsetDateTime botRegisteredAt;

    @Column(name = "support_chat_created_at")
    private OffsetDateTime supportChatCreatedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "connected_at")
    private OffsetDateTime connectedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PortalStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PortalInstallation() {
    }

    public PortalInstallation(PortalRole role, String clientCode, String title, String domain) {
        this.role = role;
        this.clientCode = clientCode;
        this.title = title;
        this.domain = domain;
        this.status = PortalStatus.DRAFT;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void markUpdated() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public PortalRole getRole() {
        return role;
    }

    public void setRole(PortalRole role) {
        this.role = role;
    }

    public String getClientCode() {
        return clientCode;
    }

    public void setClientCode(String clientCode) {
        this.clientCode = clientCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getBotId() {
        return botId;
    }

    public void setBotId(String botId) {
        this.botId = botId;
    }

    public String getBotCode() {
        return botCode;
    }

    public void setBotCode(String botCode) {
        this.botCode = botCode;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getBotType() {
        return botType;
    }

    public void setBotType(String botType) {
        this.botType = botType;
    }

    public String getSupportChatId() {
        return supportChatId;
    }

    public void setSupportChatId(String supportChatId) {
        this.supportChatId = supportChatId;
    }

    public String getSupportDialogId() {
        return supportDialogId;
    }

    public void setSupportDialogId(String supportDialogId) {
        this.supportDialogId = supportDialogId;
    }

    public OffsetDateTime getBotRegisteredAt() {
        return botRegisteredAt;
    }

    public void setBotRegisteredAt(OffsetDateTime botRegisteredAt) {
        this.botRegisteredAt = botRegisteredAt;
    }

    public OffsetDateTime getSupportChatCreatedAt() {
        return supportChatCreatedAt;
    }

    public void setSupportChatCreatedAt(OffsetDateTime supportChatCreatedAt) {
        this.supportChatCreatedAt = supportChatCreatedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(OffsetDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }

    public PortalStatus getStatus() {
        return status;
    }

    public void setStatus(PortalStatus status) {
        this.status = status;
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
