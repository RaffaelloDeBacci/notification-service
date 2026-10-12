package com.qualifyze.notification.application.port.in.audit;

import java.time.Duration;

public interface RelayAuditOutboxUseCase {

    /** Publishes up to {@code maxBatchSize} unpublished audit events to the broker, in order. */
    int relayPendingEvents(int maxBatchSize);

    /** Deletes events relayed longer ago than {@code retention}. */
    int purgeRelayedEvents(Duration retention);
}
