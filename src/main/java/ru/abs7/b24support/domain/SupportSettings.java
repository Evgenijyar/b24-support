package ru.abs7.b24support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "support_settings")
public class SupportSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "closed_chat_retention_days", nullable = false)
    private int closedChatRetentionDays;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SupportSettings() {
    }

    public SupportSettings(int closedChatRetentionDays) {
        this.id = SINGLETON_ID;
        this.closedChatRetentionDays = closedChatRetentionDays;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public int getClosedChatRetentionDays() {
        return closedChatRetentionDays;
    }

    public void setClosedChatRetentionDays(int closedChatRetentionDays) {
        this.closedChatRetentionDays = closedChatRetentionDays;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
