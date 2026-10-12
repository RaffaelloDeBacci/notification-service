package com.qualifyze.notification.domain.model;

import java.time.LocalDate;

/**
 * Signals that a certificate of the notification's site (e.g. a GMP certificate) is
 * approaching its expiry date, so affected customers can be warned ahead of time.
 */
public record CertificateExpiryPayload(
        String certificateType,
        String issuingAuthority,
        LocalDate expiryDate
) implements NotificationPayload {
    public CertificateExpiryPayload {
        certificateType = Require.text(certificateType, "certificateType");
        issuingAuthority = Require.text(issuingAuthority, "issuingAuthority");
        Require.present(expiryDate, "expiryDate");
    }

    @Override
    public MessageType messageType() {
        return MessageType.CERTIFICATE_EXPIRY;
    }
}
