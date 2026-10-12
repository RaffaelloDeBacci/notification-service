package com.qualifyze.notification.application.usecase.audit;

import com.qualifyze.notification.application.port.in.audit.RelayAuditOutboxUseCase;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxReaderPort;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import com.qualifyze.notification.application.port.out.messaging.AuditEventBrokerPort;
import com.qualifyze.notification.application.port.out.observability.NotificationMetricsPort;
import jakarta.inject.Named;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Named
public class RelayAuditOutboxService implements RelayAuditOutboxUseCase {

    private final AuditOutboxReaderPort auditOutboxReaderPort;
    private final AuditEventBrokerPort auditEventBrokerPort;
    private final NotificationMetricsPort notificationMetricsPort;

    public RelayAuditOutboxService(
            AuditOutboxReaderPort auditOutboxReaderPort,
            AuditEventBrokerPort auditEventBrokerPort,
            NotificationMetricsPort notificationMetricsPort
    ) {
        this.auditOutboxReaderPort = auditOutboxReaderPort;
        this.auditEventBrokerPort = auditEventBrokerPort;
        this.notificationMetricsPort = notificationMetricsPort;
    }

    @Override
    public int relayPendingEvents(int maxBatchSize) {
        List<SequencedAuditEvent> pending = auditOutboxReaderPort.findUnpublished(maxBatchSize);
        try {
            for (SequencedAuditEvent event : pending) {
                auditEventBrokerPort.publish(event);
                auditOutboxReaderPort.markPublished(event.sequence());
                notificationMetricsPort.incrementAuditOutboxPublished();
            }
            return pending.size();
        } finally {
            recordAuditOutboxMetrics();
        }
    }

    /**
     * The outbox is a queue, not an archive: once an event has been relayed to the broker
     * the durable copy lives in the audit service, so the local row can be dropped.
     */
    @Override
    public int purgeRelayedEvents(Duration retention) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        return auditOutboxReaderPort.deletePublishedBefore(cutoff);
    }

    private void recordAuditOutboxMetrics() {
        long depth = auditOutboxReaderPort.countUnpublished();
        notificationMetricsPort.recordAuditOutboxDepth(depth);
        OffsetDateTime oldestUnpublishedAt = auditOutboxReaderPort.findOldestUnpublishedAt();
        long oldestAgeSeconds = oldestUnpublishedAt == null
                ? 0
                : ChronoUnit.SECONDS.between(oldestUnpublishedAt, OffsetDateTime.now(oldestUnpublishedAt.getOffset()));
        notificationMetricsPort.recordAuditOutboxOldestAgeSeconds(Math.max(oldestAgeSeconds, 0));
    }
}
