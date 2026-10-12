package com.qualifyze.notification.application.port.out.audit;

import java.time.OffsetDateTime;

public record AuditEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String actor,
        OffsetDateTime occurredAt,
        String payloadJson
) {
}
