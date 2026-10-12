package com.qualifyze.notification.boot.integration;

import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.application.model.NotificationInboxEntry;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxReaderPort;
import com.qualifyze.notification.application.port.out.audit.AuditOutboxWriterPort;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import com.qualifyze.notification.application.port.out.persistence.DispatchLedgerPort;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.application.port.out.persistence.NotificationInboxPort;
import com.qualifyze.notification.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PostgresAdaptersIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NotificationInboxPort notificationInboxPort;

    @Autowired
    private DistributionListPort distributionListPort;

    @Autowired
    private DispatchLedgerPort dispatchLedgerPort;

    @Autowired
    private AuditOutboxWriterPort auditOutboxWriterPort;

    @Autowired
    private AuditOutboxReaderPort auditOutboxReaderPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldPersistClaimAndUpdateEventStatusUsingRealPostgresAndLiquibaseSchema() {
        // Arrange
        NotificationRequest event = new NotificationRequest(
                "idem-db-1",
                new Site("Site A", "Address A"),
                Set.of("customer-z", "customer-a"),
                new RegulatoryEventPayload("EMA", "OAI", LocalDate.of(2026, 10, 7), 87)
        );

        // Act
        NotificationInboxUpsertResult upsertResult = notificationInboxPort.upsert(event);
        Optional<NotificationInboxEntry> claimed = notificationInboxPort.claimNext();
        Optional<NotificationInboxEntry> secondClaim = notificationInboxPort.claimNext();
        String statusAfterClaim = jdbcTemplate.queryForObject(
                "SELECT status FROM notification_inbox WHERE event_reference = ?",
                String.class,
                upsertResult.eventReference()
        );
        notificationInboxPort.markStatus(upsertResult.eventReference(), NotificationStatus.COMPLETED);

        // Assert
        assertFalse(upsertResult.duplicate());
        assertEquals(NotificationStatus.RECEIVED, upsertResult.status());
        assertEquals(upsertResult.eventReference(), claimed.orElseThrow().eventReference());
        assertTrue(secondClaim.isEmpty(), "an event under a live lease must not be claimed again");
        assertEquals(NotificationStatus.DISPATCHING.name(), statusAfterClaim);
        assertEquals(
                NotificationStatus.COMPLETED.name(),
                jdbcTemplate.queryForObject(
                        "SELECT status FROM notification_inbox WHERE event_reference = ?",
                        String.class,
                        upsertResult.eventReference()
                )
        );
    }

    @Test
    void shouldReturnDuplicateResultWhenSameIdempotencyKeyIsInsertedTwice() {
        // Arrange
        NotificationRequest firstEvent = new NotificationRequest(
                "idem-db-2",
                new Site("Site B", "Address B"),
                Set.of("customer-1"),
                new RegulatoryEventPayload("FDA", "WARNING_LETTER", LocalDate.of(2026, 10, 8), 70)
        );
        NotificationRequest secondEvent = new NotificationRequest(
                "idem-db-2",
                new Site("Site C", "Address C"),
                Set.of("customer-2"),
                new RegulatoryEventPayload("FDA", "WARNING_LETTER", LocalDate.of(2026, 10, 9), 71)
        );

        // Act
        NotificationInboxUpsertResult firstResult = notificationInboxPort.upsert(firstEvent);
        NotificationInboxUpsertResult secondResult = notificationInboxPort.upsert(secondEvent);

        // Assert
        assertFalse(firstResult.duplicate());
        assertTrue(secondResult.duplicate());
        assertEquals(firstResult.eventReference(), secondResult.eventReference());
        assertEquals("idem-db-2", secondResult.idempotencyKey());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_inbox WHERE idempotency_key = ?",
                Integer.class,
                "idem-db-2"
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_outbox WHERE event_type = 'notification.received' AND aggregate_id = ?",
                Integer.class,
                firstResult.eventReference()
        ));
        assertEquals("Site B", jdbcTemplate.queryForObject(
                "SELECT payload -> 'payload' -> 'site' ->> 'name' FROM audit_outbox WHERE event_type = 'notification.received'",
                String.class
        ));
    }

    @Test
    void shouldWriteAuditEventInSameTransactionAsChange() {
        // Arrange
        distributionListPort.replaceForCustomer(
                new DistributionList(
                        "customer-42",
                        Set.of(
                                new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"),
                                new DistributionListTarget(DistributionChannel.SLACK, "#ops")
                        )
                ),
                "alice"
        );

        // Act
        List<DistributionListTarget> targets = distributionListPort.findTargets("customer-42");

        // Assert
        assertEquals(2, targets.size());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_outbox WHERE aggregate_id = ?",
                Integer.class,
                "customer-42"
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM distribution_list WHERE customer_reference = ?",
                Integer.class,
                "customer-42"
        ));
    }

    @Test
    void shouldWriteRemovalAuditEventsWhenTargetsAreReplaced() {
        // Arrange
        distributionListPort.replaceForCustomer(
                new DistributionList(
                        "customer-77",
                        Set.of(
                                new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"),
                                new DistributionListTarget(DistributionChannel.SLACK, "#ops")
                        )
                ),
                "alice"
        );

        // Act
        distributionListPort.replaceForCustomer(
                new DistributionList(
                        "customer-77",
                        Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"))
                ),
                "bob"
        );

        // Assert
        assertEquals(1, distributionListPort.findTargets("customer-77").size());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class,
                "customer-77",
                "notification.distribution-list.target-removed"
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class,
                "customer-77",
                "notification.distribution-list.target-added"
        ));
    }

    @Test
    void shouldCarryMaximumLengthIdempotencyKeyAndCustomerReferenceThroughInboxAndLedger() {
        // Arrange
        String idempotencyKey = "k".repeat(255);
        String customerReference = "c".repeat(64);
        NotificationRequest event = new NotificationRequest(
                idempotencyKey,
                new Site("Site Max", "Address Max"),
                Set.of(customerReference),
                new RegulatoryEventPayload("FDA", "WARNING_LETTER", LocalDate.of(2026, 6, 12), 80)
        );
        DistributionListTarget target = new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com");

        // Act
        NotificationInboxUpsertResult upsertResult = notificationInboxPort.upsert(event);
        distributionListPort.replaceForCustomer(new DistributionList(customerReference, Set.of(target)), "alice");
        dispatchLedgerPort.recordAttempt(new DispatchAttempt(
                idempotencyKey,
                customerReference,
                target,
                1,
                DispatchOutcome.DELIVERED,
                null
        ));

        // Assert
        assertFalse(upsertResult.duplicate());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispatch_ledger WHERE idempotency_key = ? AND customer_reference = ?",
                Integer.class,
                idempotencyKey,
                customerReference
        ));
        assertEquals(List.of(target), distributionListPort.findTargets(customerReference));
    }

    @Test
    void shouldKeepUnchangedTargetRowsWhenListIsReplaced() {
        // Arrange
        DistributionListTarget kept = new DistributionListTarget(DistributionChannel.EMAIL, "kept@example.com");
        distributionListPort.replaceForCustomer(
                new DistributionList("customer-diff", Set.of(
                        kept,
                        new DistributionListTarget(DistributionChannel.EMAIL, "gone@example.com")
                )),
                "alice"
        );
        Long keptIdBefore = keptRowId("customer-diff", "kept@example.com");

        // Act
        distributionListPort.replaceForCustomer(
                new DistributionList("customer-diff", Set.of(
                        kept,
                        new DistributionListTarget(DistributionChannel.EMAIL, "new@example.com")
                )),
                "bob"
        );

        // Assert
        assertEquals(keptIdBefore, keptRowId("customer-diff", "kept@example.com"));
        assertEquals(
                Set.of("kept@example.com", "new@example.com"),
                Set.copyOf(jdbcTemplate.queryForList(
                        "SELECT notification_target FROM distribution_list WHERE customer_reference = ?",
                        String.class,
                        "customer-diff"
                ))
        );
    }

    @Test
    void shouldReclaimEventWhoseLeaseExpired() {
        // Arrange
        String eventReference = notificationInboxPort.upsert(simpleEvent("idem-lease-1")).eventReference();
        notificationInboxPort.claimNext().orElseThrow();
        jdbcTemplate.update(
                "UPDATE notification_inbox SET claimed_at = now() - INTERVAL '10 minutes' WHERE event_reference = ?",
                eventReference
        );

        // Act
        Optional<NotificationInboxEntry> reclaimed = notificationInboxPort.claimNext();

        // Assert
        assertEquals(eventReference, reclaimed.orElseThrow().eventReference());
        assertEquals(2, claimCount(eventReference));
    }

    @Test
    void shouldNotReclaimEventWhoseLeaseWasRenewed() {
        // Arrange
        String eventReference = notificationInboxPort.upsert(simpleEvent("idem-lease-2")).eventReference();
        notificationInboxPort.claimNext().orElseThrow();
        jdbcTemplate.update(
                "UPDATE notification_inbox SET claimed_at = now() - INTERVAL '10 minutes' WHERE event_reference = ?",
                eventReference
        );

        // Act
        notificationInboxPort.renewLease(eventReference);

        // Assert
        assertTrue(notificationInboxPort.claimNext().isEmpty());
    }

    @Test
    void shouldFailAndAuditPoisonEventInsteadOfReclaimingIt() {
        // Arrange
        String eventReference = notificationInboxPort.upsert(simpleEvent("idem-poison")).eventReference();
        jdbcTemplate.update(
                "UPDATE notification_inbox SET status = 'DISPATCHING', claim_count = 5, "
                        + "claimed_at = now() - INTERVAL '10 minutes' WHERE event_reference = ?",
                eventReference
        );

        // Act
        Optional<NotificationInboxEntry> claimed = notificationInboxPort.claimNext();

        // Assert
        assertTrue(claimed.isEmpty());
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM notification_inbox WHERE event_reference = ?", String.class, eventReference));
        assertEquals("max claim attempts exceeded", jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM notification_inbox WHERE event_reference = ?", String.class, eventReference));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_outbox WHERE event_type = 'notification.status-changed' "
                        + "AND aggregate_id = ? AND payload ->> 'status' = 'FAILED'",
                Integer.class,
                eventReference
        ));
    }

    @Test
    void shouldClaimOldestEventsFirstAndEachOnlyOnce() {
        // Arrange
        String first = notificationInboxPort.upsert(simpleEvent("idem-order-1")).eventReference();
        String second = notificationInboxPort.upsert(simpleEvent("idem-order-2")).eventReference();

        // Act
        String claimedFirst = notificationInboxPort.claimNext().orElseThrow().eventReference();
        String claimedSecond = notificationInboxPort.claimNext().orElseThrow().eventReference();

        // Assert
        assertEquals(List.of(first, second), List.of(claimedFirst, claimedSecond));
        assertTrue(notificationInboxPort.claimNext().isEmpty());
    }

    @Test
    void shouldReportOnlyDeliveredAttemptsAsDelivered() {
        // Arrange
        DistributionListTarget delivered = new DistributionListTarget(DistributionChannel.EMAIL, "ok@example.com");
        DistributionListTarget failed = new DistributionListTarget(DistributionChannel.EMAIL, "ko@example.com");
        dispatchLedgerPort.recordAttempt(new DispatchAttempt("idem-ledger", "customer-1", failed, 1, DispatchOutcome.FAILED, "boom"));
        dispatchLedgerPort.recordAttempt(new DispatchAttempt("idem-ledger", "customer-1", delivered, 1, DispatchOutcome.FAILED, "boom"));
        dispatchLedgerPort.recordAttempt(new DispatchAttempt("idem-ledger", "customer-1", delivered, 2, DispatchOutcome.DELIVERED, null));

        // Act + Assert
        assertTrue(dispatchLedgerPort.isDelivered("idem-ledger", "customer-1", delivered));
        assertFalse(dispatchLedgerPort.isDelivered("idem-ledger", "customer-1", failed));
        assertFalse(dispatchLedgerPort.isDelivered("idem-ledger", "customer-2", delivered));
        assertFalse(dispatchLedgerPort.isDelivered("idem-other", "customer-1", delivered));
    }

    private static NotificationRequest simpleEvent(String idempotencyKey) {
        return new NotificationRequest(
                idempotencyKey,
                new Site("Site L", "Address L"),
                Set.of("customer-1"),
                new RegulatoryEventPayload("EMA", "OAI", LocalDate.of(2026, 10, 7), 60)
        );
    }

    private int claimCount(String eventReference) {
        return jdbcTemplate.queryForObject(
                "SELECT claim_count FROM notification_inbox WHERE event_reference = ?", Integer.class, eventReference);
    }

    private Long keptRowId(String customerReference, String target) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM distribution_list WHERE customer_reference = ? AND notification_target = ?",
                Long.class,
                customerReference,
                target
        );
    }

    @Test
    void shouldMarkPublishedAndPurgeRelayedOutboxRows() {
        // Arrange
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> auditOutboxWriterPort.publish(new AuditEvent(
                "11111111-1111-1111-1111-111111111111",
                "notification.distribution-list.target-added",
                "DistributionList",
                "customer-purge",
                "alice",
                OffsetDateTime.now(),
                "{\"notificationTarget\":\"ops@example.com\"}"
        )));
        List<SequencedAuditEvent> pending = auditOutboxReaderPort.findUnpublished(10);
        assertEquals(1, pending.size());
        assertEquals(1, auditOutboxReaderPort.countUnpublished());
        assertNotNull(auditOutboxReaderPort.findOldestUnpublishedAt());

        // Act
        auditOutboxReaderPort.markPublished(pending.getFirst().sequence());

        // Assert
        assertEquals(0, auditOutboxReaderPort.countUnpublished());
        assertNull(auditOutboxReaderPort.findOldestUnpublishedAt());
        assertEquals(0, auditOutboxReaderPort.deletePublishedBefore(OffsetDateTime.now().minusDays(7)));
        assertEquals(1, auditOutboxReaderPort.deletePublishedBefore(OffsetDateTime.now().plusMinutes(1)));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_outbox", Integer.class));
    }

    @Test
    void shouldWriteNoAuditEventWhenTransactionRollsBack() {
        // Arrange
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // Act
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            distributionListPort.replaceForCustomer(
                    new DistributionList(
                            "customer-99",
                            Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"))
                    ),
                    "alice"
            );
            throw new RuntimeException("boom");
        }));

        // Assert
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM distribution_list WHERE customer_reference = ?",
                Integer.class,
                "customer-99"
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_outbox WHERE aggregate_id = ?",
                Integer.class,
                "customer-99"
        ));
    }

    @Test
    void shouldRejectPublishingAuditEventOutsideTransaction() {
        // Arrange
        AuditEvent event = new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.distribution-list.target-added",
                "DistributionList",
                "customer-1",
                "alice",
                java.time.OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"ops@example.com\"}"
        );

        // Act + Assert
        assertThrows(IllegalTransactionStateException.class, () -> auditOutboxWriterPort.publish(event));
    }
}
