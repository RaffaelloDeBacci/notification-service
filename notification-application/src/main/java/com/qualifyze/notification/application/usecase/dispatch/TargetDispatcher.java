package com.qualifyze.notification.application.usecase.dispatch;

import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.application.port.out.notification.NonRetryableDeliveryException;
import com.qualifyze.notification.application.port.out.observability.NotificationMetricsPort;
import com.qualifyze.notification.application.port.out.persistence.DispatchLedgerPort;
import com.qualifyze.notification.application.port.out.render.RenderedMessage;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Delivers a rendered message to a single distribution list target, retrying transient
 * failures with exponential backoff and recording every attempt to the metrics port
 * and the dispatch ledger.
 */
@Named
public class TargetDispatcher {

    private static final int MAX_ERROR_DETAILS_LENGTH = 1000;

    private final NotificationSenderFactory notificationSenderFactory;
    private final DispatchLedgerPort dispatchLedger;
    private final NotificationMetricsPort notificationMetricsPort;
    private final DispatchRetryPolicy retryPolicy;
    private final BackoffSleeper sleeper;

    @Inject
    public TargetDispatcher(
            NotificationSenderFactory notificationSenderFactory,
            DispatchLedgerPort dispatchLedger,
            NotificationMetricsPort notificationMetricsPort,
            DispatchRetryPolicy retryPolicy
    ) {
        this(notificationSenderFactory, dispatchLedger, notificationMetricsPort, retryPolicy, BackoffSleeper.THREAD_SLEEP);
    }

    TargetDispatcher(
            NotificationSenderFactory notificationSenderFactory,
            DispatchLedgerPort dispatchLedger,
            NotificationMetricsPort notificationMetricsPort,
            DispatchRetryPolicy retryPolicy,
            BackoffSleeper sleeper
    ) {
        this.notificationSenderFactory = notificationSenderFactory;
        this.dispatchLedger = dispatchLedger;
        this.notificationMetricsPort = notificationMetricsPort;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    /** Returns whether the target was delivered within the allowed attempts. */
    public boolean deliver(
            String idempotencyKey,
            String customerReference,
            DistributionListTarget target,
            RenderedMessage renderedMessage
    ) {
        NotificationDispatchRequest request = new NotificationDispatchRequest(
                target,
                renderedMessage.subject(),
                renderedMessage.body()
        );
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            boolean retryAttempt = attempt > 1;
            long attemptStart = System.nanoTime();
            try {
                notificationSenderFactory.forChannel(target.channel()).send(request);
                notificationMetricsPort.recordDeliverySucceeded(target.channel(), System.nanoTime() - attemptStart, retryAttempt);
                dispatchLedger.recordAttempt(new DispatchAttempt(
                        idempotencyKey, customerReference, target, attempt, DispatchOutcome.DELIVERED, null));
                return true;
            } catch (Exception exception) {
                boolean permanent = exception instanceof NonRetryableDeliveryException;
                notificationMetricsPort.recordDeliveryFailed(
                        target.channel(), System.nanoTime() - attemptStart, retryAttempt, permanent);
                dispatchLedger.recordAttempt(new DispatchAttempt(
                        idempotencyKey, customerReference, target, attempt, DispatchOutcome.FAILED,
                        errorDetails(exception)));
                if (permanent || attempt == retryPolicy.maxAttempts() || !sleepBeforeAttempt(attempt + 1)) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean sleepBeforeAttempt(int nextAttempt) {
        try {
            sleeper.sleep(backoffBefore(nextAttempt));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** initialBackoff × 2^(n-2) for attempt n, plus up to 50% random jitter. */
    private Duration backoffBefore(int nextAttempt) {
        long baseMillis = retryPolicy.initialBackoff().toMillis() << (nextAttempt - 2);
        long jitterMillis = baseMillis == 0 ? 0 : ThreadLocalRandom.current().nextLong(baseMillis / 2 + 1);
        return Duration.ofMillis(baseMillis + jitterMillis);
    }

    private static String errorDetails(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() <= MAX_ERROR_DETAILS_LENGTH ? message : message.substring(0, MAX_ERROR_DETAILS_LENGTH);
    }
}
