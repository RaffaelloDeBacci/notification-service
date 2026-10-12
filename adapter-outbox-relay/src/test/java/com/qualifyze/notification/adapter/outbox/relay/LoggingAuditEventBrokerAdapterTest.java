package com.qualifyze.notification.adapter.outbox.relay;

import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

class LoggingAuditEventBrokerAdapterTest {

    @Test
    void shouldNotThrowWhenPublishingAuditEvent() {
        LoggingAuditEventBrokerAdapter adapter = new LoggingAuditEventBrokerAdapter();
        adapter.publish(new SequencedAuditEvent(1L, new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.distribution-list.target-added",
                "DistributionList",
                "customer-1",
                "alice",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"deliveryChannel\":\"EMAIL\"}"
        )));
    }
}
