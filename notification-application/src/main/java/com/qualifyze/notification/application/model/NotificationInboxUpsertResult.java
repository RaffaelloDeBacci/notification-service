package com.qualifyze.notification.application.model;

public record NotificationInboxUpsertResult(
        String eventReference,
        String idempotencyKey,
        NotificationStatus status,
        boolean duplicate
) {
}
