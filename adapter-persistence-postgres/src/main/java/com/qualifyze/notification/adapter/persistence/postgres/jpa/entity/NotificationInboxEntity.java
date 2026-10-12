package com.qualifyze.notification.adapter.persistence.postgres.jpa.entity;

import com.qualifyze.notification.application.model.NotificationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Single envelope for every notification, regardless of message type.
 *
 * <p>The type-specific body lives in the {@code payload} {@code jsonb} column
 * (schema-on-read): the inbox only ever filters on {@code status}/{@code created_at}
 * and never queries payload fields, so one row shape fits all types. Adding a new
 * message type needs no migration and no new entity. {@code message_type} is kept
 * as a plain column purely for operability (filtering/metrics in SQL).
 */
@Entity
@Table(name = "notification_inbox")
public class NotificationInboxEntity {

    @Id
    @Column(name = "event_reference", nullable = false, length = 64)
    private String eventReference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "message_type", nullable = false, length = 64)
    private String messageType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "claim_count", nullable = false)
    private int claimCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NotificationInboxEntity() {
    }

    public NotificationInboxEntity(
            String eventReference,
            String idempotencyKey,
            NotificationStatus status,
            String messageType,
            String payload
    ) {
        this.eventReference = eventReference;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.messageType = messageType;
        this.payload = payload;
    }

    public String getEventReference() {
        return eventReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getPayload() {
        return payload;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public int getClaimCount() {
        return claimCount;
    }

    public void markStatus(NotificationStatus newStatus) {
        this.status = newStatus;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }
}
