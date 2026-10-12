package com.qualifyze.notification.application.port.out.messaging;

import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;

public interface AuditEventBrokerPort {
    void publish(SequencedAuditEvent event);
}
