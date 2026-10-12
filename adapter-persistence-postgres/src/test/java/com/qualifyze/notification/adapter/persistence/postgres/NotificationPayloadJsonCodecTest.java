package com.qualifyze.notification.adapter.persistence.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.domain.model.CertificateExpiryPayload;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationPayloadJsonCodecTest {

    private final NotificationPayloadJsonCodec codec = new NotificationPayloadJsonCodec();

    @Test
    void shouldRoundTripRegulatoryEventPreservingAllFields() {
        NotificationRequest original = new NotificationRequest(
                "idem-1",
                new Site("Site A", "Address A"),
                Set.of("customer-a", "customer-b"),
                new RegulatoryEventPayload("EMA", "OAI", LocalDate.of(2026, 10, 7), 87)
        );

        NotificationRequest restored = codec.fromJson("idem-1", codec.toJson(original));

        assertEquals(original, restored);
    }

    @Test
    void shouldRoundTripCertificateExpiryPreservingAllFields() {
        NotificationRequest original = certificateRequest();

        NotificationRequest restored = codec.fromJson("idem-cert", codec.toJson(original));

        assertEquals(original, restored);
        assertInstanceOf(CertificateExpiryPayload.class, restored.payload());
    }

    @Test
    void shouldStoreEnvelopeWithTypedDetailsAndWithoutIdempotencyKey() throws Exception {
        JsonNode json = new ObjectMapper().readTree(codec.toJson(new NotificationRequest(
                "idem-2",
                new Site("Site A", "Address A"),
                Set.of("customer-b", "customer-a"),
                new RegulatoryEventPayload("FDA", "WARNING_LETTER", LocalDate.of(2026, 10, 8), 70)
        )));

        assertEquals("Site A", json.at("/site/name").asText());
        assertEquals("customer-a", json.at("/affectedCustomerReferences/0").asText());
        assertEquals("customer-b", json.at("/affectedCustomerReferences/1").asText());
        assertEquals("RegulatoryEventPayload", json.at("/details/@type").asText());
        assertEquals("2026-10-08", json.at("/details/issueDate").asText());
        assertFalse(json.has("idempotencyKey"));
    }

    @Test
    void shouldEmbedTypeDiscriminatorToDistinguishSubtypes() {
        String json = codec.toJson(certificateRequest());

        assertTrue(json.contains("\"@type\":\"CertificateExpiryPayload\""));
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenStoredJsonIsMalformed() {
        assertThrows(IllegalStateException.class, () -> codec.fromJson("idem-3", "{not json"));
    }

    private static NotificationRequest certificateRequest() {
        return new NotificationRequest(
                "idem-cert",
                new Site("Site B", "Address B"),
                Set.of("customer-x"),
                new CertificateExpiryPayload("GMP_CERTIFICATE", "EMA", LocalDate.of(2027, 1, 15))
        );
    }
}
