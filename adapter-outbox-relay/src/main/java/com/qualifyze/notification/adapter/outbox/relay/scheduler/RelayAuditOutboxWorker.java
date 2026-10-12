package com.qualifyze.notification.adapter.outbox.relay.scheduler;

import com.qualifyze.notification.application.port.in.audit.RelayAuditOutboxUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "notification.audit.outbox.worker.enabled", havingValue = "true", matchIfMissing = true)
public class RelayAuditOutboxWorker {

    private final RelayAuditOutboxUseCase relayAuditOutboxUseCase;
    private final int batchSize;
    private final Duration retention;

    public RelayAuditOutboxWorker(
            RelayAuditOutboxUseCase relayAuditOutboxUseCase,
            @Value("${notification.audit.outbox.worker.batch-size:50}") int batchSize,
            @Value("${notification.audit.outbox.retention:P7D}") Duration retention
    ) {
        this.relayAuditOutboxUseCase = relayAuditOutboxUseCase;
        this.batchSize = batchSize;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${notification.audit.outbox.worker-delay-ms:1000}")
    public void processOutbox() {
        relayAuditOutboxUseCase.relayPendingEvents(batchSize);
    }

    @Scheduled(fixedDelayString = "${notification.audit.outbox.purge-delay-ms:3600000}")
    public void purgeRelayedEvents() {
        relayAuditOutboxUseCase.purgeRelayedEvents(retention);
    }
}
