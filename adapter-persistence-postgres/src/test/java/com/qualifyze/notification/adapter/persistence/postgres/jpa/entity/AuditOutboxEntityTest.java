package com.qualifyze.notification.adapter.persistence.postgres.jpa.entity;

import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditOutboxEntityTest {

    @Test
    void shouldMapAuditEventToOutboxEntity() {
        // Arrange
        AuditEvent event = new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.distribution-list.replaced",
                "DistributionList",
                "customer-1",
                "alice",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"ops@example.com\"}"
        );

        // Act
        AuditOutboxEntity result = AuditOutboxEntity.from(event);

        // Assert
        assertEquals(event.eventId(), result.getEventId().toString());
        assertEquals(event.eventType(), result.getEventType());
        assertEquals(event.aggregateType(), result.getAggregateType());
        assertEquals(event.aggregateId(), result.getAggregateId());
        assertEquals(event.actor(), result.getActor());
        assertEquals(event.occurredAt(), result.getOccurredAt());
        assertEquals(event.payloadJson(), result.getPayload());
    }

    @Test
    void shouldRejectInvalidEventIdWhenMappingAuditEventToOutboxEntity() {
        AuditEvent event = new AuditEvent(
                "not-a-uuid",
                "notification.distribution-list.replaced",
                "DistributionList",
                "customer-1",
                "alice",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{}"
        );

        assertThrows(IllegalArgumentException.class, () -> AuditOutboxEntity.from(event));
    }
}
