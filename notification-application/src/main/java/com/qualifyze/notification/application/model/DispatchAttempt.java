package com.qualifyze.notification.application.model;

import com.qualifyze.notification.domain.model.DistributionListTarget;

/** One delivery attempt against one distribution list target. */
public record DispatchAttempt(
        String idempotencyKey,
        String customerReference,
        DistributionListTarget target,
        int attemptNumber,
        DispatchOutcome outcome,
        String errorDetails
) {
}
