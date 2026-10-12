package com.qualifyze.notification.adapter.persistence.postgres;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.NotificationInboxEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.NotificationInboxJpaRepository;
import com.qualifyze.notification.application.model.NotificationInboxEntry;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostgresNotificationInboxAdapterTest {

    private final NotificationPayloadJsonCodec payloadJsonCodec = new NotificationPayloadJsonCodec();

    @Mock
    private NotificationInboxJpaRepository notificationInboxJpaRepository;

    @Mock
    private AuditOutboxWriterPort auditOutboxWriterPort;

    private PostgresNotificationInboxAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PostgresNotificationInboxAdapter(
                notificationInboxJpaRepository,
                auditOutboxWriterPort,
                payloadJsonCodec,
                Duration.ofMinutes(5),
                5
        );
    }

    @Test
    void shouldInsertEventAsJsonbEnvelopeAndReturnNonDuplicateWhenUpsertSucceeds() {
        // Arrange
        NotificationRequest request = regulatoryRequest("idem-1", Set.of("customer-b", "customer-a"));
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        when(notificationInboxJpaRepository.insertIfAbsent(anyString(), eq("idem-1"), eq("REGULATORY_EVENT"), anyString()))
                .thenReturn(1);

        // Act
        NotificationInboxUpsertResult result = adapter.upsert(request);

        // Assert
        verify(notificationInboxJpaRepository).insertIfAbsent(
                eq(result.eventReference()), eq("idem-1"), eq("REGULATORY_EVENT"), payloadCaptor.capture());
        NotificationRequest stored = payloadJsonCodec.fromJson("idem-1", payloadCaptor.getValue());
        assertEquals(request, stored);
        assertEquals(Set.of("customer-a", "customer-b"), stored.affectedCustomerReferences());
        assertFalse(result.duplicate());
        assertEquals("idem-1", result.idempotencyKey());
        assertEquals(NotificationStatus.RECEIVED, result.status());
        assertTrue(result.eventReference() != null && !result.eventReference().isBlank());
    }

    @Test
    void shouldPublishReceivedAuditEventWithFullPayloadWhenEventIsInserted() {
        // Arrange
        NotificationRequest request = regulatoryRequest("idem-audit", Set.of("customer-1"));
        when(notificationInboxJpaRepository.insertIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        NotificationInboxUpsertResult result = adapter.upsert(request);

        // Assert
        verify(auditOutboxWriterPort).publish(auditCaptor.capture());
        AuditEvent audit = auditCaptor.getValue();
        assertEquals("notification.received", audit.eventType());
        assertEquals("Notification", audit.aggregateType());
        assertEquals(result.eventReference(), audit.aggregateId());
        assertTrue(audit.payloadJson().contains("\"idempotencyKey\":\"idem-audit\""));
        assertTrue(audit.payloadJson().contains("\"messageType\":\"REGULATORY_EVENT\""));
        assertTrue(audit.payloadJson().contains("\"authorityName\":\"EMA\""));
    }

    @Test
    void shouldStoreSecondMessageTypeThroughTheSameGenericEnvelope() {
        // Arrange
        NotificationRequest request = certificateRequest("idem-cert", Set.of("customer-x"));
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        when(notificationInboxJpaRepository.insertIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);

        // Act
        adapter.upsert(request);

        // Assert
        verify(notificationInboxJpaRepository).insertIfAbsent(
                anyString(), eq("idem-cert"), eq("CERTIFICATE_EXPIRY"), payloadCaptor.capture());
        NotificationRequest stored = payloadJsonCodec.fromJson("idem-cert", payloadCaptor.getValue());
        assertTrue(stored.payload() instanceof CertificateExpiryPayload);
        assertEquals("GMP_CERTIFICATE", ((CertificateExpiryPayload) stored.payload()).certificateType());
    }

    @Test
    void shouldReturnExistingEventWithoutAuditWhenIdempotencyKeyAlreadyExists() {
        // Arrange
        NotificationRequest request = regulatoryRequest("idem-2", Set.of("customer-1"));
        NotificationInboxEntity existing = regulatoryEntity("event-ref-2", "idem-2", Set.of("customer-1"), NotificationStatus.DISPATCHING);
        when(notificationInboxJpaRepository.insertIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(notificationInboxJpaRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.of(existing));

        // Act
        NotificationInboxUpsertResult result = adapter.upsert(request);

        // Assert
        assertTrue(result.duplicate());
        assertEquals("event-ref-2", result.eventReference());
        assertEquals("idem-2", result.idempotencyKey());
        assertEquals(NotificationStatus.DISPATCHING, result.status());
        verifyNoInteractions(auditOutboxWriterPort);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenDuplicateLookupFindsNothing() {
        // Arrange
        NotificationRequest request = regulatoryRequest("idem-3", Set.of("customer-1"));
        when(notificationInboxJpaRepository.insertIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(notificationInboxJpaRepository.findByIdempotencyKey("idem-3")).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(IllegalStateException.class, () -> adapter.upsert(request));
    }

    @Test
    void shouldClaimNextEntryAndMapItBackToTypedNotificationRequest() {
        // Arrange
        NotificationInboxEntity claimed = regulatoryEntity("event-ref-4", "idem-4", Set.of("customer-1", "customer-2"), NotificationStatus.DISPATCHING);
        when(notificationInboxJpaRepository.failExhaustedClaims(300L, 5, PostgresNotificationInboxAdapter.MAX_CLAIMS_EXCEEDED))
                .thenReturn(List.of());
        when(notificationInboxJpaRepository.claimNext(300L, 5)).thenReturn(Optional.of(claimed));

        // Act
        Optional<NotificationInboxEntry> result = adapter.claimNext();

        // Assert
        assertTrue(result.isPresent());
        NotificationInboxEntry entry = result.get();
        assertEquals("event-ref-4", entry.eventReference());
        assertEquals("idem-4", entry.request().idempotencyKey());
        assertEquals(Set.of("customer-1", "customer-2"), entry.request().affectedCustomerReferences());
        assertInstanceOf(RegulatoryEventPayload.class, entry.request().payload());
        verifyNoInteractions(auditOutboxWriterPort);
    }

    @Test
    void shouldReturnEmptyWhenNothingIsClaimable() {
        // Arrange
        when(notificationInboxJpaRepository.failExhaustedClaims(anyLong(), anyInt(), anyString())).thenReturn(List.of());
        when(notificationInboxJpaRepository.claimNext(anyLong(), anyInt())).thenReturn(Optional.empty());

        // Act + Assert
        assertTrue(adapter.claimNext().isEmpty());
    }

    @Test
    void shouldAuditPoisonEventsFailedBeforeClaiming() {
        // Arrange
        NotificationInboxEntity poison = regulatoryEntity("event-ref-5", "idem-5", Set.of("customer-1"), NotificationStatus.FAILED);
        when(notificationInboxJpaRepository.failExhaustedClaims(300L, 5, PostgresNotificationInboxAdapter.MAX_CLAIMS_EXCEEDED))
                .thenReturn(List.of(poison));
        when(notificationInboxJpaRepository.claimNext(300L, 5)).thenReturn(Optional.empty());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        adapter.claimNext();

        // Assert
        verify(auditOutboxWriterPort).publish(auditCaptor.capture());
        AuditEvent audit = auditCaptor.getValue();
        assertEquals("notification.status-changed", audit.eventType());
        assertEquals("event-ref-5", audit.aggregateId());
        assertTrue(audit.payloadJson().contains("\"status\":\"FAILED\""));
        assertTrue(audit.payloadJson().contains(PostgresNotificationInboxAdapter.MAX_CLAIMS_EXCEEDED));
    }

    @Test
    void shouldRenewLeaseThroughRepository() {
        // Act
        adapter.renewLease("event-ref-6");

        // Assert
        verify(notificationInboxJpaRepository).renewLease("event-ref-6");
    }

    @Test
    void shouldRejectInvalidLeaseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresNotificationInboxAdapter(
                notificationInboxJpaRepository, auditOutboxWriterPort, payloadJsonCodec, Duration.ZERO, 5));
        assertThrows(IllegalArgumentException.class, () -> new PostgresNotificationInboxAdapter(
                notificationInboxJpaRepository, auditOutboxWriterPort, payloadJsonCodec, Duration.ofMinutes(5), 0));
    }

    @Test
    void shouldMarkStatusWhenReferenceExists() {
        // Arrange
        NotificationInboxEntity existing = regulatoryEntity("event-ref-6", "idem-6", Set.of("customer-1"), NotificationStatus.DISPATCHING);
        when(notificationInboxJpaRepository.findById("event-ref-6")).thenReturn(Optional.of(existing));

        // Act
        adapter.markStatus("event-ref-6", NotificationStatus.COMPLETED);

        // Assert
        assertEquals(NotificationStatus.COMPLETED, existing.getStatus());
        verify(notificationInboxJpaRepository).save(existing);
        verify(auditOutboxWriterPort).publish(any(AuditEvent.class));
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenMarkStatusReferenceIsMissing() {
        // Arrange
        when(notificationInboxJpaRepository.findById("missing")).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(IllegalStateException.class, () -> adapter.markStatus("missing", NotificationStatus.COMPLETED));
    }

    @Test
    void shouldMarkFailedWhenReferenceExists() {
        // Arrange
        NotificationInboxEntity existing = regulatoryEntity("event-ref-7", "idem-7", Set.of("customer-1"), NotificationStatus.DISPATCHING);
        when(notificationInboxJpaRepository.findById("event-ref-7")).thenReturn(Optional.of(existing));

        // Act
        adapter.markFailed("event-ref-7", "delivery failed");

        // Assert
        assertEquals(NotificationStatus.FAILED, existing.getStatus());
        assertEquals("delivery failed", getField(existing, "failureReason"));
        verify(notificationInboxJpaRepository).save(existing);
        verify(auditOutboxWriterPort).publish(any(AuditEvent.class));
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenMarkFailedReferenceIsMissing() {
        // Arrange
        when(notificationInboxJpaRepository.findById("missing")).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(IllegalStateException.class, () -> adapter.markFailed("missing", "error"));
    }

    private static NotificationRequest regulatoryRequest(String idempotencyKey, Set<String> affectedCustomers) {
        return new NotificationRequest(
                idempotencyKey,
                new Site("Site A", "Address A"),
                affectedCustomers,
                new RegulatoryEventPayload("EMA", "DOC", LocalDate.of(2026, 10, 7), 90)
        );
    }

    private static NotificationRequest certificateRequest(String idempotencyKey, Set<String> affectedCustomers) {
        return new NotificationRequest(
                idempotencyKey,
                new Site("Site A", "Address A"),
                affectedCustomers,
                new CertificateExpiryPayload("GMP_CERTIFICATE", "EMA", LocalDate.of(2026, 12, 31))
        );
    }

    private NotificationInboxEntity regulatoryEntity(
            String eventReference,
            String idempotencyKey,
            Set<String> affectedCustomerReferences,
            NotificationStatus status
    ) {
        NotificationRequest request = new NotificationRequest(
                idempotencyKey,
                new Site("Site A", "Address A"),
                affectedCustomerReferences,
                new RegulatoryEventPayload("EMA", "DOC", LocalDate.of(2026, 10, 7), 90)
        );
        return new NotificationInboxEntity(
                eventReference,
                idempotencyKey,
                status,
                request.messageType().name(),
                payloadJsonCodec.toJson(request)
        );
    }

    private static Object getField(Object instance, String name) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exception) {
                throw new AssertionError("Cannot read field " + name, exception);
            }
        }
        throw new AssertionError("Field not found: " + name);
    }
}
