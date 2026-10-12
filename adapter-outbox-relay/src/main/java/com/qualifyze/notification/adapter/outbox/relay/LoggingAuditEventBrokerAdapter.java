package com.qualifyze.notification.adapter.outbox.relay;

import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import com.qualifyze.notification.application.port.out.messaging.AuditEventBrokerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.audit.broker", havingValue = "logging", matchIfMissing = true)
public class LoggingAuditEventBrokerAdapter implements AuditEventBrokerPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAuditEventBrokerAdapter.class);

    @Override
    public void publish(SequencedAuditEvent sequencedEvent) {
        AuditEvent event = sequencedEvent.event();
        LOGGER.info(
                "Publishing audit event sequence={} eventId={} eventType={} aggregateType={} aggregateId={}",
                sequencedEvent.sequence(),
                event.eventId(),
                event.eventType(),
                event.aggregateType(),
                event.aggregateId()
        );
    }
}
