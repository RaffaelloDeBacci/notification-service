package com.qualifyze.notification.application.usecase.audit;

import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxReaderPort;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import com.qualifyze.notification.application.port.out.messaging.AuditEventBrokerPort;
import com.qualifyze.notification.application.port.out.observability.NotificationMetricsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelayAuditOutboxServiceTest {

    @Mock
    private AuditOutboxReaderPort auditOutboxReaderPort;

    @Mock
    private AuditEventBrokerPort auditEventBrokerPort;

    @Mock
    private NotificationMetricsPort notificationMetricsPort;

    @Test
    void shouldPublishBeforeMarkingAndReturnBatchSize() {
        // Arrange
        RelayAuditOutboxService service = new RelayAuditOutboxService(auditOutboxReaderPort, auditEventBrokerPort, notificationMetricsPort);
        SequencedAuditEvent first = event(1L, "event-1");
        SequencedAuditEvent second = event(2L, "event-2");
        when(auditOutboxReaderPort.findUnpublished(10)).thenReturn(List.of(first, second));
        when(auditOutboxReaderPort.countUnpublished()).thenReturn(0L);
        when(auditOutboxReaderPort.findOldestUnpublishedAt()).thenReturn(null);

        // Act
        int processed = service.relayPendingEvents(10);

        // Assert
        assertEquals(2, processed);
        InOrder inOrder = inOrder(auditEventBrokerPort, auditOutboxReaderPort);
        inOrder.verify(auditEventBrokerPort).publish(first);
        inOrder.verify(auditOutboxReaderPort).markPublished(1L);
        inOrder.verify(auditEventBrokerPort).publish(second);
        inOrder.verify(auditOutboxReaderPort).markPublished(2L);
        verify(notificationMetricsPort, org.mockito.Mockito.times(2)).incrementAuditOutboxPublished();
        verify(notificationMetricsPort).recordAuditOutboxDepth(0L);
        verify(notificationMetricsPort).recordAuditOutboxOldestAgeSeconds(0L);
    }

    @Test
    void shouldStopBatchOnPublishFailure() {
        // Arrange
        RelayAuditOutboxService service = new RelayAuditOutboxService(auditOutboxReaderPort, auditEventBrokerPort, notificationMetricsPort);
        SequencedAuditEvent first = event(1L, "event-1");
        SequencedAuditEvent second = event(2L, "event-2");
        when(auditOutboxReaderPort.findUnpublished(5)).thenReturn(List.of(first, second));
        when(auditOutboxReaderPort.countUnpublished()).thenReturn(2L);
        when(auditOutboxReaderPort.findOldestUnpublishedAt()).thenReturn(OffsetDateTime.parse("2026-10-08T12:00:00+02:00"));
        org.mockito.Mockito.doThrow(new RuntimeException("broker down")).when(auditEventBrokerPort).publish(first);

        // Act + Assert
        assertThrows(RuntimeException.class, () -> service.relayPendingEvents(5));
        verify(auditEventBrokerPort).publish(first);
        verify(auditEventBrokerPort, never()).publish(second);
        verify(auditOutboxReaderPort, never()).markPublished(1L);
        verify(notificationMetricsPort).recordAuditOutboxDepth(2L);
    }

    @Test
    void shouldPurgeRelayedEventsOlderThanRetention() {
        // Arrange
        RelayAuditOutboxService service = new RelayAuditOutboxService(auditOutboxReaderPort, auditEventBrokerPort, notificationMetricsPort);
        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        when(auditOutboxReaderPort.deletePublishedBefore(cutoffCaptor.capture())).thenReturn(3);

        // Act
        int purged = service.purgeRelayedEvents(Duration.ofDays(7));

        // Assert
        assertEquals(3, purged);
        OffsetDateTime cutoff = cutoffCaptor.getValue();
        assertTrue(cutoff.isBefore(OffsetDateTime.now().minusDays(6)));
        assertTrue(cutoff.isAfter(OffsetDateTime.now().minusDays(8)));
    }

    private static SequencedAuditEvent event(long sequence, String eventId) {
        return new SequencedAuditEvent(sequence, new AuditEvent(
                eventId,
                "notification.distribution-list.target-added",
                "DistributionList",
                "customer-1",
                "alice",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"deliveryChannel\":\"EMAIL\"}"
        ));
    }
}
