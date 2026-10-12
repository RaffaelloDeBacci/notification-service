package com.qualifyze.notification.adapter.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.DispatchLedgerEntity;
import com.qualifyze.notification.adapter.persistence.postgres.jpa.repository.DispatchLedgerJpaRepository;
import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.application.port.out.persistence.DispatchLedgerPort;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class PostgresDispatchLedgerAdapter implements DispatchLedgerPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DispatchLedgerJpaRepository dispatchLedgerJpaRepository;
    private final AuditOutboxWriterPort auditOutboxWriterPort;

    public PostgresDispatchLedgerAdapter(
            DispatchLedgerJpaRepository dispatchLedgerJpaRepository,
            AuditOutboxWriterPort auditOutboxWriterPort
    ) {
        this.dispatchLedgerJpaRepository = dispatchLedgerJpaRepository;
        this.auditOutboxWriterPort = auditOutboxWriterPort;
    }

    @Override
    @Transactional
    public void recordAttempt(DispatchAttempt attempt) {
        dispatchLedgerJpaRepository.save(DispatchLedgerEntity.from(attempt));
        auditOutboxWriterPort.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "notification.delivery.attempt-recorded",
                "DispatchAttempt",
                attempt.idempotencyKey(),
                "system",
                OffsetDateTime.now(),
                payloadAsJson(attempt)
        ));
    }

    @Override
    public boolean isDelivered(String idempotencyKey, String customerReference, DistributionListTarget target) {
        return dispatchLedgerJpaRepository.existsByIdempotencyKeyAndCustomerReferenceAndDeliveryChannelAndNotificationTargetAndOutcome(
                idempotencyKey,
                customerReference,
                target.channel(),
                target.destination(),
                DispatchOutcome.DELIVERED
        );
    }

    private String payloadAsJson(DispatchAttempt attempt) {
        try {
            return OBJECT_MAPPER.writeValueAsString(new DispatchAttemptPayload(
                    attempt.customerReference(),
                    attempt.target().channel().name(),
                    attempt.target().destination(),
                    attempt.attemptNumber(),
                    attempt.outcome().name(),
                    attempt.errorDetails()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize dispatch attempt payload", exception);
        }
    }

    private record DispatchAttemptPayload(
            String customerReference,
            String deliveryChannel,
            String notificationTarget,
            int attemptNumber,
            String outcome,
            String errorDetails
    ) {
    }
}
