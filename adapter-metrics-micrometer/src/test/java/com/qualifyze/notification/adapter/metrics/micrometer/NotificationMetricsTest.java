package com.qualifyze.notification.adapter.metrics.micrometer;

import com.qualifyze.notification.domain.model.DistributionChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationMetricsTest {

    @Test
    void shouldIncrementTopLevelCountersWhenEventsAndCustomersAreRecorded() {
        // Arrange
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(meterRegistry);

        // Act
        metrics.incrementEventsProcessed();
        metrics.incrementCustomersNotified();
        metrics.incrementCustomersSkippedWithoutDistributionList();
        metrics.incrementAuditOutboxPublished();

        // Assert
        Counter processed = meterRegistry.find("notification.events.processed").counter();
        Counter notified = meterRegistry.find("notification.customers.notified").counter();
        Counter skipped = meterRegistry.find("notification.customers.skipped")
                .tag("reason", "no_distribution_list")
                .counter();
        Counter published = meterRegistry.find("notification.audit.outbox.published").counter();
        assertNotNull(processed);
        assertNotNull(notified);
        assertNotNull(skipped);
        assertNotNull(published);
        assertEquals(1.0, processed.count());
        assertEquals(1.0, notified.count());
        assertEquals(1.0, skipped.count());
        assertEquals(1.0, published.count());
    }

    @Test
    void shouldRecordSuccessfulDispatchAttemptMetricsWithExpectedTags() {
        // Arrange
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(meterRegistry);

        // Act
        metrics.recordDeliverySucceeded(DistributionChannel.EMAIL, 1_000_000L, false);

        // Assert
        Counter attempts = meterRegistry.find("notification.dispatch.attempts")
                .tag("channel", "email")
                .tag("result", "success")
                .tag("retry", "false")
                .tag("error_type", "none")
                .counter();
        Timer latency = meterRegistry.find("notification.dispatch.latency")
                .tag("channel", "email")
                .tag("result", "success")
                .tag("retry", "false")
                .timer();
        assertNotNull(attempts);
        assertNotNull(latency);
        assertEquals(1.0, attempts.count());
        assertEquals(1L, latency.count());
    }

    @Test
    void shouldRecordTransientAndPermanentFailuresWithProperErrorTags() {
        // Arrange
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(meterRegistry);

        // Act
        metrics.recordDeliveryFailed(DistributionChannel.SLACK, 2_000_000L, true, false);
        metrics.recordDeliveryFailed(DistributionChannel.SLACK, 3_000_000L, true, true);

        // Assert
        Counter transientFailures = meterRegistry.find("notification.dispatch.attempts")
                .tag("channel", "slack")
                .tag("result", "failure")
                .tag("retry", "true")
                .tag("error_type", "transient")
                .counter();
        Counter permanentFailures = meterRegistry.find("notification.dispatch.attempts")
                .tag("channel", "slack")
                .tag("result", "failure")
                .tag("retry", "true")
                .tag("error_type", "permanent")
                .counter();
        assertNotNull(transientFailures);
        assertNotNull(permanentFailures);
        assertEquals(1.0, transientFailures.count());
        assertEquals(1.0, permanentFailures.count());
    }

    @Test
    void shouldRecordAuditOutboxDepthAndOldestAgeGauges() {
        // Arrange
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(meterRegistry);

        // Act
        metrics.recordAuditOutboxDepth(7L);
        metrics.recordAuditOutboxOldestAgeSeconds(42L);

        // Assert
        assertEquals(7.0, meterRegistry.get("notification.audit.outbox.depth").gauge().value());
        assertEquals(42.0, meterRegistry.get("notification.audit.outbox.oldest_age_seconds").gauge().value());
    }
}
