package com.qualifyze.notification.adapter.outbox.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SnsAuditEventBrokerAdapterTest {

    @Mock
    private SnsClient snsClient;

    @Test
    void shouldPublishStructuredMessageToFifoTopicWithOrderingFields() throws Exception {
        // Arrange
        SnsAuditEventBrokerAdapter adapter = new SnsAuditEventBrokerAdapter(
                snsClient,
                "arn:aws:sns:eu-central-1:000000000000:notification-audit-events.fifo",
                "notification-service",
                "qualifyze-notification-service"
        );
        SequencedAuditEvent event = new SequencedAuditEvent(17L, new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.delivery.attempt-recorded",
                "DispatchAttempt",
                "idem-17",
                "system",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"outcome\":\"DELIVERED\"}"
        ));

        // Act
        adapter.publish(event);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        PublishRequest request = requestCaptor.getValue();
        assertEquals("arn:aws:sns:eu-central-1:000000000000:notification-audit-events.fifo", request.topicArn());
        assertEquals("notification-service", request.messageGroupId());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", request.messageDeduplicationId());

        JsonNode body = new ObjectMapper().readTree(request.message());
        assertEquals(17L, body.get("sequence").asLong());
        assertEquals("qualifyze-notification-service", body.get("sourceService").asText());
        assertEquals("notification.delivery.attempt-recorded", body.get("eventType").asText());
        assertEquals("DELIVERED", body.get("payload").get("outcome").asText());
        assertNotNull(body.get("relayedAt"));
    }

    @Test
    void shouldPublishWithoutFifoFieldsForStandardTopic() {
        // Arrange
        SnsAuditEventBrokerAdapter adapter = new SnsAuditEventBrokerAdapter(
                snsClient,
                "arn:aws:sns:eu-central-1:000000000000:notification-audit-events",
                "ignored-group",
                "qualifyze-notification-service"
        );
        SequencedAuditEvent event = new SequencedAuditEvent(1L, new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.delivery.attempt-recorded",
                "DispatchAttempt",
                "idem-1",
                "system",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"outcome\":\"DELIVERED\"}"
        ));

        // Act
        adapter.publish(event);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        PublishRequest request = requestCaptor.getValue();
        assertTrue(request.messageGroupId() == null || request.messageGroupId().isBlank());
        assertTrue(request.messageDeduplicationId() == null || request.messageDeduplicationId().isBlank());
    }

    @Test
    void shouldFailWhenPayloadJsonIsInvalid() {
        // Arrange
        SnsAuditEventBrokerAdapter adapter = new SnsAuditEventBrokerAdapter(
                snsClient,
                "arn:aws:sns:eu-central-1:000000000000:notification-audit-events",
                "notification-service",
                "qualifyze-notification-service"
        );
        SequencedAuditEvent event = new SequencedAuditEvent(1L, new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.delivery.attempt-recorded",
                "DispatchAttempt",
                "idem-1",
                "system",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{not-valid-json"
        ));

        // Act + Assert
        assertThrows(IllegalStateException.class, () -> adapter.publish(event));
        verifyNoInteractions(snsClient);
    }
}
