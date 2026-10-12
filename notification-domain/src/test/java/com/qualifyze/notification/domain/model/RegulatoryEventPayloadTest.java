package com.qualifyze.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegulatoryEventPayloadTest {

    @Test
    void shouldCreatePayloadWhenValidInputsProvided() {
        // Act
        RegulatoryEventPayload result = new RegulatoryEventPayload("BfArM", "RECALL_NOTICE", LocalDate.of(2026, 10, 7), 100);

        // Assert
        assertEquals("BfArM", result.authorityName());
        assertEquals("RECALL_NOTICE", result.documentTypeCode());
        assertEquals(LocalDate.of(2026, 10, 7), result.issueDate());
        assertEquals(100, result.confidenceLevel());
        assertEquals(MessageType.REGULATORY_EVENT, result.messageType());
    }

    @Test
    void shouldAllowMinimumConfidenceLevel() {
        assertEquals(0, new RegulatoryEventPayload("FDA", "ALERT", LocalDate.now(), 0).confidenceLevel());
    }

    @Test
    void shouldRejectBlankOrMissingAuthorityName() {
        assertEquals("authorityName is required", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload(" ", "DOC", LocalDate.now(), 50)).getMessage());
        assertEquals("authorityName is required", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload(null, "DOC", LocalDate.now(), 50)).getMessage());
    }

    @Test
    void shouldRejectBlankOrMissingDocumentTypeCode() {
        assertEquals("documentTypeCode is required", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload("Authority", "", LocalDate.now(), 50)).getMessage());
        assertEquals("documentTypeCode is required", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload("Authority", null, LocalDate.now(), 50)).getMessage());
    }

    @Test
    void shouldRejectMissingIssueDate() {
        assertEquals("issueDate is required", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload("Authority", "DOC", null, 50)).getMessage());
    }

    @Test
    void shouldRejectConfidenceLevelOutsideZeroToHundred() {
        assertEquals("confidenceLevel must be between 0 and 100", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload("Authority", "DOC", LocalDate.now(), -1)).getMessage());
        assertEquals("confidenceLevel must be between 0 and 100", assertThrows(IllegalArgumentException.class,
                () -> new RegulatoryEventPayload("Authority", "DOC", LocalDate.now(), 101)).getMessage());
    }
}
