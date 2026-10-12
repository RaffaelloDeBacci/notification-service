package com.qualifyze.notification.application.port.out.audit;

/**
 * An {@link AuditEvent} once the outbox has assigned it a monotonic sequence.
 *
 * <p>The sequence is what lets the consuming audit service detect gaps, so it is part
 * of the broker contract rather than an internal storage detail.
 */
public record SequencedAuditEvent(long sequence, AuditEvent event) {
}
