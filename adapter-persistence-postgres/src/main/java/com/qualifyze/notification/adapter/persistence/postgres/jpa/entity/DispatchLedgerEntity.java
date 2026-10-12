package com.qualifyze.notification.adapter.persistence.postgres.jpa.entity;

import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.domain.model.DistributionChannel;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dispatch_ledger")
public class DispatchLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "customer_reference", nullable = false)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false)
    private DistributionChannel deliveryChannel;

    @Column(name = "notification_target", nullable = false)
    private String notificationTarget;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private DispatchOutcome outcome;

    @Column(name = "error_details")
    private String errorDetails;

    @CreationTimestamp
    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private OffsetDateTime dispatchedAt;

    protected DispatchLedgerEntity() {
    }

    private DispatchLedgerEntity(DispatchAttempt attempt) {
        this.idempotencyKey = attempt.idempotencyKey();
        this.customerReference = attempt.customerReference();
        this.deliveryChannel = attempt.target().channel();
        this.notificationTarget = attempt.target().destination();
        this.attemptNumber = attempt.attemptNumber();
        this.outcome = attempt.outcome();
        this.errorDetails = attempt.errorDetails();
    }

    public static DispatchLedgerEntity from(DispatchAttempt attempt) {
        return new DispatchLedgerEntity(attempt);
    }
}
