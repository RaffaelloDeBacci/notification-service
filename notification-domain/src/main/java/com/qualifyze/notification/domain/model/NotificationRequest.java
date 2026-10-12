package com.qualifyze.notification.domain.model;

import java.util.Set;

/**
 * Uniform envelope for every notification entering the inbox: the identity
 * ({@code idempotencyKey}), the site concerned, the customers to notify, and the
 * type-specific {@link NotificationPayload}.
 */
public record NotificationRequest(
        String idempotencyKey,
        Site site,
        Set<String> affectedCustomerReferences,
        NotificationPayload payload
) {
    public NotificationRequest {
        idempotencyKey = Require.text(idempotencyKey, "idempotencyKey");
        Require.present(site, "site");
        affectedCustomerReferences = Require.notEmpty(affectedCustomerReferences, "affectedCustomerReferences");
        Require.present(payload, "payload");
    }

    public MessageType messageType() {
        return payload.messageType();
    }
}
