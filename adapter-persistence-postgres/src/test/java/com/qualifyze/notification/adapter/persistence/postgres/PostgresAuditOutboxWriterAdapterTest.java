package com.qualifyze.notification.adapter.persistence.postgres;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.AuditOutboxEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.AuditOutboxJpaRepository;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostgresAuditOutboxWriterAdapterTest {

    @Mock
    private AuditOutboxJpaRepository auditOutboxJpaRepository;

    @InjectMocks
    private PostgresAuditOutboxWriterAdapter publisher;

    @Test
    void shouldPersistAuditEventToOutbox() {
        // Arrange
        AuditEvent event = new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.distribution-list.target-added",
                "DistributionList",
                "customer-1",
                "alice",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"ops@example.com\"}"
        );

        // Act
        publisher.publish(event);

        // Assert
        ArgumentCaptor<AuditOutboxEntity> captor = ArgumentCaptor.forClass(AuditOutboxEntity.class);
        verify(auditOutboxJpaRepository).save(captor.capture());
        assertEquals(event.eventId(), captor.getValue().getEventId().toString());
        assertEquals(event.payloadJson(), captor.getValue().getPayload());
    }
}
