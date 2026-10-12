package com.qualifyze.notification.boot.integration;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.in.dispatch.DispatchInboxUseCase;
import com.qualifyze.notification.application.port.in.ingest.IngestNotificationUseCase;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.domain.model.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        GREEN_MAIL.start();
    }

    @Autowired
    private IngestNotificationUseCase ingestNotificationUseCase;

    @Autowired
    private DispatchInboxUseCase dispatchInboxUseCase;

    @Autowired
    private DistributionListPort distributionListPort;

    @DynamicPropertySource
    static void configureMail(DynamicPropertyRegistry registry) {
        registry.add("notification.channel.email.host", () -> "localhost");
        registry.add("notification.channel.email.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("notification.channel.email.username", () -> "");
        registry.add("notification.channel.email.password", () -> "");
    }

    @BeforeEach
    void resetMailServer() throws FolderException {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @AfterAll
    static void stopMailServer() {
        GREEN_MAIL.stop();
    }

    @Test
    void shouldIngestAndDispatchEventEndToEndWithRealDatabaseAndSuccessfulEmailDelivery() throws Exception {
        // Arrange
        distributionListPort.replaceForCustomer(
                new DistributionList(
                        "customer-flow-1",
                        Set.of(new DistributionListTarget(DistributionChannel.EMAIL, "flow@example.com"))
                ),
                "integration-test"
        );
        NotificationRequest request = new NotificationRequest(
                "idem-flow-1",
                new Site("Site Flow", "Flow Street"),
                Set.of("customer-flow-1"),
                new RegulatoryEventPayload("EMA", "OAI", LocalDate.of(2026, 10, 7), 95)
        );

        // Act
        NotificationInboxUpsertResult processingResult = ingestNotificationUseCase.ingest(request);
        int processedEvents = dispatchInboxUseCase.dispatchPendingEvents(10);

        // Assert
        assertEquals(NotificationStatus.RECEIVED, processingResult.status());
        assertFalse(processingResult.duplicate());
        assertEquals(1, processedEvents);
        assertEquals(
                "COMPLETED",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM notification_inbox WHERE event_reference = ?",
                        String.class,
                        processingResult.eventReference()
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dispatch_ledger WHERE idempotency_key = ? AND outcome = 'DELIVERED'",
                        Integer.class,
                        "idem-flow-1"
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_outbox WHERE event_type = ? AND aggregate_id = ?",
                        Integer.class,
                        "notification.delivery.attempt-recorded",
                        "idem-flow-1"
                )
        );
        assertTrue(GREEN_MAIL.waitForIncomingEmail(5_000, 1));
        MimeMessage receivedMessage = GREEN_MAIL.getReceivedMessages()[0];
        assertEquals("flow@example.com", receivedMessage.getAllRecipients()[0].toString());
        assertEquals("Regulatory event detected", receivedMessage.getSubject());
        assertTrue(receivedMessage.getContent().toString().contains("Site Flow"));
        assertTrue(receivedMessage.getContent().toString().contains("EMA"));
    }
}
