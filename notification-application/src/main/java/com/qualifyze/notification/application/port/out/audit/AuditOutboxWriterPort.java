package com.qualifyze.notification.application.port.out.audit;

public interface AuditOutboxWriterPort {
    void publish(AuditEvent event);
}
