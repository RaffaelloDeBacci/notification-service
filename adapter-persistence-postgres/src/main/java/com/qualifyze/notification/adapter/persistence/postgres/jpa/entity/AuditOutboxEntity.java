package com.qualifyze.notification.adapter.persistence.postgres.jpa.entity;

import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_outbox")
public class AuditOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence", nullable = false, updatable = false)
    private Long sequence;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected AuditOutboxEntity() {
    }

    private AuditOutboxEntity(
            UUID eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String actor,
            OffsetDateTime occurredAt,
            String payload
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }

    public static AuditOutboxEntity from(AuditEvent event) {
        return new AuditOutboxEntity(
                UUID.fromString(event.eventId()),
                event.eventType(),
                event.aggregateType(),
                event.aggregateId(),
                event.actor(),
                event.occurredAt(),
                event.payloadJson()
        );
    }

    public Long getSequence() {
        return sequence;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getActor() {
        return actor;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void markPublished(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public SequencedAuditEvent toSequencedAuditEvent() {
        return new SequencedAuditEvent(sequence, new AuditEvent(
                eventId.toString(),
                eventType,
                aggregateType,
                aggregateId,
                actor,
                occurredAt,
                payload
        ));
    }
}
