package com.qualifyze.notification.adapter.persistence.postgres;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.DispatchLedgerEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.DispatchLedgerJpaRepository;
import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresDispatchLedgerAdapterTest {

    @Mock
    private DispatchLedgerJpaRepository dispatchLedgerJpaRepository;

    @Mock
    private AuditOutboxWriterPort auditOutboxWriterPort;

    @InjectMocks
    private PostgresDispatchLedgerAdapter adapter;

    @Test
    void shouldReportDeliveredWhenLedgerHoldsDeliveredAttemptForTarget() {
        // Arrange
        DistributionListTarget target = new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com");
        when(dispatchLedgerJpaRepository.existsByIdempotencyKeyAndCustomerReferenceAndDeliveryChannelAndNotificationTargetAndOutcome(
                "idem-1", "customer-1", DistributionChannel.EMAIL, "ops@example.com", DispatchOutcome.DELIVERED))
                .thenReturn(true);

        // Act + Assert
        assertTrue(adapter.isDelivered("idem-1", "customer-1", target));
    }

    @Test
    void shouldPersistDispatchAttemptInLedger() {
        // Arrange
        ArgumentCaptor<DispatchLedgerEntity> entityCaptor = ArgumentCaptor.forClass(DispatchLedgerEntity.class);

        // Act
        adapter.recordAttempt(new DispatchAttempt(
                "idem-1",
                "customer-1",
                new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"),
                2,
                DispatchOutcome.FAILED,
                "temporary outage"
        ));

        // Assert
        verify(dispatchLedgerJpaRepository).save(entityCaptor.capture());
        DispatchLedgerEntity entity = entityCaptor.getValue();
        assertEquals("idem-1", getField(entity, "idempotencyKey"));
        assertEquals("customer-1", getField(entity, "customerReference"));
        assertEquals(DistributionChannel.EMAIL, getField(entity, "deliveryChannel"));
        assertEquals("ops@example.com", getField(entity, "notificationTarget"));
        assertEquals(2, getField(entity, "attemptNumber"));
        assertEquals(DispatchOutcome.FAILED, getField(entity, "outcome"));
        assertEquals("temporary outage", getField(entity, "errorDetails"));

        ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditOutboxWriterPort).publish(auditEventCaptor.capture());
        AuditEvent auditEvent = auditEventCaptor.getValue();
        assertEquals("notification.delivery.attempt-recorded", auditEvent.eventType());
        assertEquals("DispatchAttempt", auditEvent.aggregateType());
        assertEquals("idem-1", auditEvent.aggregateId());
        assertEquals("system", auditEvent.actor());
        assertTrue(auditEvent.payloadJson().contains("\"customerReference\":\"customer-1\""));
        assertTrue(auditEvent.payloadJson().contains("\"deliveryChannel\":\"EMAIL\""));
        assertTrue(auditEvent.payloadJson().contains("\"notificationTarget\":\"ops@example.com\""));
        assertTrue(auditEvent.payloadJson().contains("\"attemptNumber\":2"));
        assertTrue(auditEvent.payloadJson().contains("\"outcome\":\"FAILED\""));
    }

    private static Object getField(Object instance, String name) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot read field " + name, exception);
        }
    }
}
