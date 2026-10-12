package com.qualifyze.notification.domain.model;

/**
 * Type-specific facts of a notification. The set of types is closed and owned by
 * this service, so consumers {@code switch} exhaustively over the permitted records:
 * a new type that is not handled everywhere does not compile.
 */
public sealed interface NotificationPayload permits RegulatoryEventPayload, CertificateExpiryPayload {
    MessageType messageType();
}
