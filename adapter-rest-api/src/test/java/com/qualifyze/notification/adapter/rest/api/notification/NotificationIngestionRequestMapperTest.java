package com.qualifyze.notification.adapter.rest.api.notification;

import com.qualifyze.notification.adapter.rest.api.contract.model.CertificateExpiryIngestRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.IngestNotificationRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.MessageType;
import com.qualifyze.notification.adapter.rest.api.contract.model.RegulatoryEventIngestRequest;
import com.qualifyze.notification.domain.model.CertificateExpiryPayload;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationIngestionRequestMapperTest {

    private final NotificationIngestionRequestMapper mapper = new NotificationIngestionRequestMapper();

    @Test
    void shouldMapRegulatoryEventRequestToDomainRequestWhenRequestIsValid() {
        // Arrange
        RegulatoryEventIngestRequest request = new RegulatoryEventIngestRequest();
        request.setIdempotencyKey("idem-1");
        request.setMessageType(MessageType.REGULATORY_EVENT);
        request.setSiteName("Site A");
        request.setSiteAddress("Address A");
        request.setAuthorityName("EMA");
        request.setDocumentTypeCode("ALERT");
        request.setIssueDate(LocalDate.of(2026, 10, 7));
        request.setConfidenceLevel(91);
        request.setAffectedCustomerReferences(Set.of("customer-1", "customer-2"));

        // Act
        NotificationRequest result = mapper.toRequest(request);

        // Assert
        assertEquals("idem-1", result.idempotencyKey());
        assertEquals(new Site("Site A", "Address A"), result.site());
        assertEquals(Set.of("customer-1", "customer-2"), result.affectedCustomerReferences());
        RegulatoryEventPayload payload = (RegulatoryEventPayload) result.payload();
        assertEquals("EMA", payload.authorityName());
        assertEquals("ALERT", payload.documentTypeCode());
        assertEquals(LocalDate.of(2026, 10, 7), payload.issueDate());
        assertEquals(91, payload.confidenceLevel());
    }

    @Test
    void shouldMapCertificateExpiryRequestToDomainRequestWhenRequestIsValid() {
        // Arrange
        CertificateExpiryIngestRequest request = new CertificateExpiryIngestRequest();
        request.setIdempotencyKey("idem-cert");
        request.setMessageType(MessageType.CERTIFICATE_EXPIRY);
        request.setSiteName("Site A");
        request.setSiteAddress("Address A");
        request.setCertificateType("GMP_CERTIFICATE");
        request.setIssuingAuthority("EMA");
        request.setExpiryDate(LocalDate.of(2026, 12, 31));
        request.setAffectedCustomerReferences(Set.of("customer-1"));

        // Act
        NotificationRequest result = mapper.toRequest(request);

        // Assert
        assertEquals("idem-cert", result.idempotencyKey());
        assertEquals(new Site("Site A", "Address A"), result.site());
        assertEquals(Set.of("customer-1"), result.affectedCustomerReferences());
        CertificateExpiryPayload payload = (CertificateExpiryPayload) result.payload();
        assertEquals("GMP_CERTIFICATE", payload.certificateType());
        assertEquals("EMA", payload.issuingAuthority());
        assertEquals(LocalDate.of(2026, 12, 31), payload.expiryDate());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenRequestIsNull() {
        // Arrange + Act + Assert
        assertThrows(NullPointerException.class, () -> mapper.toRequest(null));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenRequestIsNotAKnownVariant() {
        // Arrange: the bare base type, as if a new discriminator value had no case yet
        IngestNotificationRequest request = new IngestNotificationRequest();
        request.setIdempotencyKey("idem-2");
        request.setMessageType(MessageType.REGULATORY_EVENT);
        request.setSiteName("Site A");
        request.setSiteAddress("Address A");
        request.setAffectedCustomerReferences(Set.of("customer-1"));

        // Act + Assert
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> mapper.toRequest(request));
        assertTrue(exception.getMessage().contains("Unsupported notification messageType"));
    }
}
