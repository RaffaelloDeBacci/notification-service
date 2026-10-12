package com.qualifyze.notification.application.port.out.observability;

import com.qualifyze.notification.domain.model.DistributionChannel;

public interface NotificationMetricsPort {

    void incrementEventsProcessed();

    void incrementCustomersNotified();

    void incrementCustomersSkippedWithoutDistributionList();

    void incrementAuditOutboxPublished();

    void recordAuditOutboxDepth(long depth);

    void recordAuditOutboxOldestAgeSeconds(long oldestAgeSeconds);

    void recordDeliverySucceeded(DistributionChannel channel, long latencyNanos, boolean retryAttempt);

    void recordDeliveryFailed(DistributionChannel channel, long latencyNanos, boolean retryAttempt, boolean permanent);
}
