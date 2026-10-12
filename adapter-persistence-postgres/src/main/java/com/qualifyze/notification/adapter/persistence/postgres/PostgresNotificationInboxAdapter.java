package com.qualifyze.notification.adapter.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.NotificationInboxEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.NotificationInboxJpaRepository;
import com.qualifyze.notification.application.model.NotificationInboxEntry;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.application.port.out.persistence.NotificationInboxPort;
import com.qualifyze.notification.domain.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresNotificationInboxAdapter implements NotificationInboxPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresNotificationInboxAdapter.class);
    static final String MAX_CLAIMS_EXCEEDED = "max claim attempts exceeded";

    private final NotificationInboxJpaRepository notificationInboxJpaRepository;
    private final AuditOutboxWriterPort auditOutboxWriterPort;
    private final NotificationPayloadJsonCodec payloadJsonCodec;
    private final Duration lease;
    private final int maxClaims;

    public PostgresNotificationInboxAdapter(
            NotificationInboxJpaRepository notificationInboxJpaRepository,
            AuditOutboxWriterPort auditOutboxWriterPort,
            NotificationPayloadJsonCodec payloadJsonCodec,
            @Value("${notification.inbox.worker.lease:PT5M}") Duration lease,
            @Value("${notification.inbox.worker.max-claims:5}") int maxClaims
    ) {
        if (lease.toSeconds() < 1) {
            throw new IllegalArgumentException("lease must be at least one second");
        }
        if (maxClaims < 1) {
            throw new IllegalArgumentException("maxClaims must be positive");
        }
        this.notificationInboxJpaRepository = notificationInboxJpaRepository;
        this.auditOutboxWriterPort = auditOutboxWriterPort;
        this.payloadJsonCodec = payloadJsonCodec;
        this.lease = lease;
        this.maxClaims = maxClaims;
    }

    @Override
    @Transactional
    public NotificationInboxUpsertResult upsert(NotificationRequest request) {
        String eventReference = UUID.randomUUID().toString();
        String messageType = request.payload().messageType().name();
        String payloadJson = payloadJsonCodec.toJson(request);
        int inserted = notificationInboxJpaRepository.insertIfAbsent(
                eventReference,
                request.idempotencyKey(),
                messageType,
                payloadJson
        );
        if (inserted == 0) {
            return findByIdempotencyKey(request.idempotencyKey(), true);
        }
        publishReceivedEvent(eventReference, request.idempotencyKey(), messageType, payloadJson);
        return new NotificationInboxUpsertResult(
                eventReference,
                request.idempotencyKey(),
                NotificationStatus.RECEIVED,
                false
        );
    }

    @Override
    @Transactional
    public Optional<NotificationInboxEntry> claimNext() {
        long leaseSeconds = lease.toSeconds();
        notificationInboxJpaRepository.failExhaustedClaims(leaseSeconds, maxClaims, MAX_CLAIMS_EXCEEDED)
                .forEach(failed -> {
                    LOGGER.warn("Failing event {} after {} claims without completion",
                            failed.getEventReference(), failed.getClaimCount());
                    publishStatusChangedEvent(failed, NotificationStatus.FAILED, MAX_CLAIMS_EXCEEDED);
                });
        return notificationInboxJpaRepository.claimNext(leaseSeconds, maxClaims)
                .map(claimed -> {
                    if (claimed.getClaimCount() > 1) {
                        LOGGER.warn("Reclaiming event {} after expired lease (claim {} of {})",
                                claimed.getEventReference(), claimed.getClaimCount(), maxClaims);
                    }
                    return toInboxEntry(claimed);
                });
    }

    @Override
    @Transactional
    public void renewLease(String eventReference) {
        notificationInboxJpaRepository.renewLease(eventReference);
    }

    @Override
    @Transactional
    public void markStatus(String eventReference, NotificationStatus status) {
        NotificationInboxEntity entity = notificationInboxJpaRepository.findById(eventReference)
                .orElseThrow(() -> new IllegalStateException("Inbox event reference not found: " + eventReference));
        entity.markStatus(status);
        notificationInboxJpaRepository.save(entity);
        publishStatusChangedEvent(entity, status, null);
    }

    @Override
    @Transactional
    public void markFailed(String eventReference, String reason) {
        NotificationInboxEntity entity = notificationInboxJpaRepository.findById(eventReference)
                .orElseThrow(() -> new IllegalStateException("Inbox event reference not found: " + eventReference));
        entity.markFailed(reason);
        notificationInboxJpaRepository.save(entity);
        publishStatusChangedEvent(entity, NotificationStatus.FAILED, reason);
    }

    private NotificationInboxUpsertResult findByIdempotencyKey(String idempotencyKey, boolean duplicate) {
        NotificationInboxEntity match = notificationInboxJpaRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to retrieve inbox event after upsert for key " + idempotencyKey
                ));
        return new NotificationInboxUpsertResult(
                match.getEventReference(),
                match.getIdempotencyKey(),
                match.getStatus(),
                duplicate
        );
    }

    private void publishReceivedEvent(
            String eventReference,
            String idempotencyKey,
            String messageType,
            String payloadJson
    ) {
        auditOutboxWriterPort.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "notification.received",
                "Notification",
                eventReference,
                "notification-api",
                OffsetDateTime.now(),
                receivedPayloadJson(idempotencyKey, messageType, payloadJson)
        ));
    }

    private String receivedPayloadJson(String idempotencyKey, String messageType, String payloadJson) {
        try {
            ObjectNode node = OBJECT_MAPPER.createObjectNode()
                    .put("idempotencyKey", idempotencyKey)
                    .put("messageType", messageType);
            node.set("payload", OBJECT_MAPPER.readTree(payloadJson));
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification received payload", exception);
        }
    }

    private NotificationInboxEntry toInboxEntry(NotificationInboxEntity entity) {
        return new NotificationInboxEntry(entity.getEventReference(), toRequest(entity));
    }

    private NotificationRequest toRequest(NotificationInboxEntity entity) {
        return payloadJsonCodec.fromJson(entity.getIdempotencyKey(), entity.getPayload());
    }

    private void publishStatusChangedEvent(
            NotificationInboxEntity entity,
            NotificationStatus status,
            String reason
    ) {
        auditOutboxWriterPort.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "notification.status-changed",
                "Notification",
                entity.getEventReference(),
                "system",
                OffsetDateTime.now(),
                statusPayloadJson(entity.getMessageType(), status, reason)
        ));
    }

    private String statusPayloadJson(String messageType, NotificationStatus status, String reason) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                    new NotificationStatusPayload(messageType, status.name(), reason)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification status payload", exception);
        }
    }

    private record NotificationStatusPayload(String messageType, String status, String reason) {
    }
}
