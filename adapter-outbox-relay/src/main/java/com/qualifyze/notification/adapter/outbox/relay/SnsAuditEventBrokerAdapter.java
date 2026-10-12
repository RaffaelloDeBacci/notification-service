package com.qualifyze.notification.adapter.outbox.relay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import com.qualifyze.notification.application.port.out.messaging.AuditEventBrokerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "notification.audit.broker", havingValue = "sns")
public class SnsAuditEventBrokerAdapter implements AuditEventBrokerPort {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;
    private final String messageGroupId;
    private final String sourceService;

    public SnsAuditEventBrokerAdapter(
            SnsClient snsClient,
            @Value("${notification.audit.sns.topic-arn}") String topicArn,
            @Value("${notification.audit.sns.message-group-id:notification-service}") String messageGroupId,
            @Value("${spring.application.name:qualifyze-notification-service}") String sourceService
    ) {
        this.snsClient = snsClient;
        this.objectMapper = new ObjectMapper();
        this.topicArn = topicArn;
        this.messageGroupId = messageGroupId;
        this.sourceService = sourceService;
    }

    @Override
    public void publish(SequencedAuditEvent sequencedEvent) {
        String message;
        try {
            message = toMessage(sequencedEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit event for SNS", exception);
        }

        AuditEvent event = sequencedEvent.event();
        PublishRequest.Builder request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(message);

        if (topicArn.endsWith(".fifo")) {
            request.messageGroupId(messageGroupId);
            request.messageDeduplicationId(event.eventId());
        }

        snsClient.publish(request.build());
    }

    private String toMessage(SequencedAuditEvent sequencedEvent) throws JsonProcessingException {
        AuditEvent event = sequencedEvent.event();
        JsonNode payload = objectMapper.readTree(event.payloadJson());
        return objectMapper.writeValueAsString(Map.of(
                "sourceService", sourceService,
                "sequence", sequencedEvent.sequence(),
                "eventId", event.eventId(),
                "eventType", event.eventType(),
                "aggregateType", event.aggregateType(),
                "aggregateId", event.aggregateId(),
                "actor", event.actor(),
                "occurredAt", event.occurredAt().toString(),
                "relayedAt", OffsetDateTime.now().toString(),
                "payload", payload
        ));
    }
}
