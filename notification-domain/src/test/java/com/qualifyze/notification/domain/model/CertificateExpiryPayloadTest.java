package com.qualifyze.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificateExpiryPayloadTest {

    @Test
    void shouldCreatePayloadAndStripText() {
        // Act
        CertificateExpiryPayload result = new CertificateExpiryPayload(" GMP_CERTIFICATE ", "EMA", LocalDate.of(2026, 12, 31));

        // Assert
        assertEquals("GMP_CERTIFICATE", result.certificateType());
        assertEquals("EMA", result.issuingAuthority());
        assertEquals(MessageType.CERTIFICATE_EXPIRY, result.messageType());
    }

    @Test
    void shouldRejectMissingFields() {
        assertEquals("certificateType is required", assertThrows(IllegalArgumentException.class,
                () -> new CertificateExpiryPayload(" ", "EMA", LocalDate.now())).getMessage());
        assertEquals("issuingAuthority is required", assertThrows(IllegalArgumentException.class,
                () -> new CertificateExpiryPayload("GMP", null, LocalDate.now())).getMessage());
        assertEquals("expiryDate is required", assertThrows(IllegalArgumentException.class,
                () -> new CertificateExpiryPayload("GMP", "EMA", null)).getMessage());
    }
}
