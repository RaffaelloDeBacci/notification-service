package com.qualifyze.notification.adapter.persistence.postgres.jpa.repository;

import com.qualifyze.notification.adapter.persistence.postgres.jpa.entity.NotificationInboxEntity;
import com.qualifyze.notification.application.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationInboxJpaRepository extends JpaRepository<NotificationInboxEntity, String> {
    Optional<NotificationInboxEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomically claims the oldest event that is new or whose lease has expired, unless it
     * has already been claimed {@code maxClaims} times. {@code SKIP LOCKED} lets concurrent
     * workers claim different events without blocking.
     */
    @Query(nativeQuery = true, value = """
            UPDATE notification_inbox
               SET status = 'DISPATCHING', claimed_at = now(), claim_count = claim_count + 1, updated_at = now()
             WHERE event_reference = (
                   SELECT event_reference
                     FROM notification_inbox
                    WHERE (status = 'RECEIVED'
                           OR (status = 'DISPATCHING' AND claimed_at < now() - :leaseSeconds * INTERVAL '1 second'))
                      AND claim_count < :maxClaims
                    ORDER BY created_at
                    LIMIT 1
                      FOR UPDATE SKIP LOCKED)
            RETURNING *
            """)
    Optional<NotificationInboxEntity> claimNext(
            @Param("leaseSeconds") long leaseSeconds,
            @Param("maxClaims") int maxClaims
    );

    /** Fails events whose lease expired after the last permitted claim (poison events). */
    @Query(nativeQuery = true, value = """
            UPDATE notification_inbox
               SET status = 'FAILED', failure_reason = :reason, updated_at = now()
             WHERE status = 'DISPATCHING'
               AND claimed_at < now() - :leaseSeconds * INTERVAL '1 second'
               AND claim_count >= :maxClaims
            RETURNING *
            """)
    List<NotificationInboxEntity> failExhaustedClaims(
            @Param("leaseSeconds") long leaseSeconds,
            @Param("maxClaims") int maxClaims,
            @Param("reason") String reason
    );

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE notification_inbox
               SET claimed_at = now()
             WHERE event_reference = :eventReference AND status = 'DISPATCHING'
            """)
    int renewLease(@Param("eventReference") String eventReference);

    /** Inserts a RECEIVED event unless the idempotency key exists; returns the number of rows inserted (0 or 1). */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notification_inbox (event_reference, idempotency_key, message_type, payload, status)
            VALUES (:eventReference, :idempotencyKey, :messageType, CAST(:payload AS jsonb), 'RECEIVED')
            ON CONFLICT (idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("eventReference") String eventReference,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("messageType") String messageType,
            @Param("payload") String payload
    );
}
