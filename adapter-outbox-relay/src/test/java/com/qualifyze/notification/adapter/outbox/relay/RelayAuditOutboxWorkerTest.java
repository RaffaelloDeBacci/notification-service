package com.qualifyze.notification.adapter.outbox.relay;

import com.qualifyze.notification.adapter.outbox.relay.scheduler.RelayAuditOutboxWorker;
import com.qualifyze.notification.application.port.in.audit.RelayAuditOutboxUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelayAuditOutboxWorkerTest {

    @Mock
    private RelayAuditOutboxUseCase relayAuditOutboxUseCase;

    @Test
    void shouldRelayPendingEventsWithConfiguredBatchSizeWhenWorkerRuns() {
        // Arrange
        RelayAuditOutboxWorker worker = new RelayAuditOutboxWorker(relayAuditOutboxUseCase, 25, Duration.ofDays(7));
        when(relayAuditOutboxUseCase.relayPendingEvents(25)).thenReturn(2);

        // Act
        worker.processOutbox();

        // Assert
        verify(relayAuditOutboxUseCase).relayPendingEvents(25);
    }

    @Test
    void shouldPurgeRelayedEventsWithConfiguredRetentionWhenPurgeRuns() {
        // Arrange
        RelayAuditOutboxWorker worker = new RelayAuditOutboxWorker(relayAuditOutboxUseCase, 25, Duration.ofDays(7));
        when(relayAuditOutboxUseCase.purgeRelayedEvents(Duration.ofDays(7))).thenReturn(3);

        // Act
        worker.purgeRelayedEvents();

        // Assert
        verify(relayAuditOutboxUseCase).purgeRelayedEvents(Duration.ofDays(7));
    }
}
