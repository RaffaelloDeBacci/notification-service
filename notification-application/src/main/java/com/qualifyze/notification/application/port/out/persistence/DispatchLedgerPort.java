package com.qualifyze.notification.application.port.out.persistence;

import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.domain.model.DistributionListTarget;

/**
 * Records the per-attempt delivery timeline.
 *
 * <p>Operational read model: the dispatcher consults it to skip targets already
 * delivered when an event is re-processed (crash recovery, manual retry).
 * Implementations also mirror attempts to the audit outbox for compliance relay.
 */
public interface DispatchLedgerPort {
    void recordAttempt(DispatchAttempt attempt);

    /** Whether a previous attempt already delivered this event to the target. */
    boolean isDelivered(String idempotencyKey, String customerReference, DistributionListTarget target);
}
