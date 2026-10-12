package com.qualifyze.notification.application.usecase.dispatch;

import java.time.Duration;

/**
 * Retry settings for a single target delivery.
 *
 * @param maxAttempts    total attempts, including the first one
 * @param initialBackoff wait before the second attempt; doubles on each further attempt
 */
public record DispatchRetryPolicy(int maxAttempts, Duration initialBackoff) {

    public DispatchRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be non-negative");
        }
    }
}
