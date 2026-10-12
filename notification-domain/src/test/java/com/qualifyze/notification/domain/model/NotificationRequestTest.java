package com.qualifyze.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationRequestTest {

    private static final Site SITE = new Site("A", "B");
    private static final Set<String> CUSTOMERS = Set.of("customer-1");

    private static RegulatoryEventPayload payload() {
        return new RegulatoryEventPayload("Authority", "DOC", LocalDate.now(), 50);
    }

    @Test
    void shouldCreateRequestAndDeriveMessageTypeFromPayload() {
        // Act
        NotificationRequest request = new NotificationRequest("idem-1", SITE, CUSTOMERS, payload());

        // Assert
        assertEquals("idem-1", request.idempotencyKey());
        assertEquals(SITE, request.site());
        assertEquals(CUSTOMERS, request.affectedCustomerReferences());
        assertEquals(MessageType.REGULATORY_EVENT, request.messageType());
    }

    @Test
    void shouldStripIdempotencyKey() {
        assertEquals("idem-1", new NotificationRequest("  idem-1  ", SITE, CUSTOMERS, payload()).idempotencyKey());
    }

    @Test
    void shouldRejectBlankOrMissingIdempotencyKey() {
        assertEquals("idempotencyKey is required", assertThrows(IllegalArgumentException.class,
                () -> new NotificationRequest(" ", SITE, CUSTOMERS, payload())).getMessage());
        assertEquals("idempotencyKey is required", assertThrows(IllegalArgumentException.class,
                () -> new NotificationRequest(null, SITE, CUSTOMERS, payload())).getMessage());
    }

    @Test
    void shouldRejectMissingSite() {
        assertEquals("site is required", assertThrows(IllegalArgumentException.class,
                () -> new NotificationRequest("idem-1", null, CUSTOMERS, payload())).getMessage());
    }

    @Test
    void shouldRejectMissingOrEmptyAffectedCustomerReferences() {
        assertEquals("affectedCustomerReferences is required", assertThrows(IllegalArgumentException.class,
                () -> new NotificationRequest("idem-1", SITE, null, payload())).getMessage());
        assertEquals("affectedCustomerReferences is required", assertThrows(IllegalArgumentException.class,
                () -> new NotificationRequest("idem-1", SITE, Set.of(), payload())).getMessage());
    }

    @Test
    void shouldRejectMissingPayload() {
        assertEquals("payload is required", assertThrows(IllegalArgumentException.class,
                () -> new NotificationRequest("idem-1", SITE, CUSTOMERS, null)).getMessage());
    }

    @Test
    void shouldDefensivelyCopyAffectedCustomerReferences() {
        // Arrange
        Set<String> input = new HashSet<>(Set.of("customer-1"));

        // Act
        NotificationRequest request = new NotificationRequest("idem-1", SITE, input, payload());
        input.add("customer-2");

        // Assert
        assertEquals(Set.of("customer-1"), request.affectedCustomerReferences());
        assertThrows(UnsupportedOperationException.class, () -> request.affectedCustomerReferences().add("customer-3"));
    }
}
