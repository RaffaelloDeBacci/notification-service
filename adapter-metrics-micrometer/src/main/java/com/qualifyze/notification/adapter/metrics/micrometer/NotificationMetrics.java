package com.qualifyze.notification.adapter.metrics.micrometer;

import com.qualifyze.notification.application.port.out.observability.NotificationMetricsPort;
import com.qualifyze.notification.domain.model.DistributionChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NotificationMetrics implements NotificationMetricsPort {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";
    private static final String ERROR_TYPE_NONE = "none";
    private static final String ERROR_TYPE_TRANSIENT = "transient";
    private static final String ERROR_TYPE_PERMANENT = "permanent";

    private final MeterRegistry meterRegistry;
    private final Counter eventsProcessed;
    private final Counter customersNotified;
    private final Counter customersSkippedWithoutDistributionList;
    private final Counter auditOutboxPublished;
    private final AtomicLong auditOutboxDepth = new AtomicLong();
    private final AtomicLong auditOutboxOldestAgeSeconds = new AtomicLong();

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.eventsProcessed = Counter.builder("notification.events.processed")
                .description("Number of processed notification events")
                .register(meterRegistry);
        this.customersNotified = Counter.builder("notification.customers.notified")
                .description("Number of customers notified by at least one successful delivery")
                .register(meterRegistry);
        this.customersSkippedWithoutDistributionList = Counter.builder("notification.customers.skipped")
                .description("Number of customers skipped because they could not be notified")
                .tag("reason", "no_distribution_list")
                .register(meterRegistry);
        this.auditOutboxPublished = Counter.builder("notification.audit.outbox.published")
                .description("Number of audit outbox events published")
                .register(meterRegistry);
        Gauge.builder("notification.audit.outbox.depth", auditOutboxDepth, AtomicLong::get)
                .description("Number of unpublished audit outbox events")
                .register(meterRegistry);
        Gauge.builder("notification.audit.outbox.oldest_age_seconds", auditOutboxOldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unpublished audit outbox event")
                .register(meterRegistry);
    }

    @Override
    public void incrementEventsProcessed() {
        eventsProcessed.increment();
    }

    @Override
    public void incrementCustomersNotified() {
        customersNotified.increment();
    }

    @Override
    public void incrementCustomersSkippedWithoutDistributionList() {
        customersSkippedWithoutDistributionList.increment();
    }

    @Override
    public void incrementAuditOutboxPublished() {
        auditOutboxPublished.increment();
    }

    @Override
    public void recordAuditOutboxDepth(long depth) {
        auditOutboxDepth.set(depth);
    }

    @Override
    public void recordAuditOutboxOldestAgeSeconds(long oldestAgeSeconds) {
        auditOutboxOldestAgeSeconds.set(oldestAgeSeconds);
    }

    @Override
    public void recordDeliverySucceeded(DistributionChannel channel, long latencyNanos, boolean retryAttempt) {
        recordAttempt(channel, latencyNanos, retryAttempt, RESULT_SUCCESS, ERROR_TYPE_NONE);
    }

    @Override
    public void recordDeliveryFailed(DistributionChannel channel, long latencyNanos, boolean retryAttempt, boolean permanent) {
        recordAttempt(channel, latencyNanos, retryAttempt, RESULT_FAILURE, permanent ? ERROR_TYPE_PERMANENT : ERROR_TYPE_TRANSIENT);
    }

    private void recordAttempt(
            DistributionChannel channel,
            long latencyNanos,
            boolean retryAttempt,
            String resultTag,
            String errorTypeTag
    ) {
        String channelTag = channel.name().toLowerCase();
        String retryTag = Boolean.toString(retryAttempt);

        Counter.builder("notification.dispatch.attempts")
                .description("Total number of dispatch attempts")
                .tag("channel", channelTag)
                .tag("result", resultTag)
                .tag("retry", retryTag)
                .tag("error_type", errorTypeTag)
                .register(meterRegistry)
                .increment();

        Timer.builder("notification.dispatch.latency")
                .description("Dispatch attempt latency")
                .tag("channel", channelTag)
                .tag("result", resultTag)
                .tag("retry", retryTag)
                .register(meterRegistry)
                .record(latencyNanos, TimeUnit.NANOSECONDS);
    }
}
