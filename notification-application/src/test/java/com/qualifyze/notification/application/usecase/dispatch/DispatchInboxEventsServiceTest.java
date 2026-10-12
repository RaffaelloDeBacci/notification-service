package com.qualifyze.notification.application.usecase.dispatch;

import com.qualifyze.notification.application.model.DispatchAttempt;
import com.qualifyze.notification.application.model.DispatchOutcome;
import com.qualifyze.notification.application.model.NotificationInboxEntry;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.out.notification.NonRetryableDeliveryException;
import com.qualifyze.notification.application.port.out.notification.NotificationSenderPort;
import com.qualifyze.notification.application.port.out.observability.NotificationMetricsPort;
import com.qualifyze.notification.application.port.out.persistence.DispatchLedgerPort;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.application.port.out.persistence.NotificationInboxPort;
import com.qualifyze.notification.application.port.out.render.MessageRenderer;
import com.qualifyze.notification.application.port.out.render.RenderedMessage;
import com.qualifyze.notification.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchInboxEventsServiceTest {

    private static final RenderedMessage RENDERED =
            new RenderedMessage("Regulatory event detected", "formatted message");
    private static final DistributionListTarget TARGET =
            new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com");

    @Mock
    private DistributionListPort distributionListPort;

    @Mock
    private NotificationSenderFactory notificationSenderFactory;

    @Mock
    private NotificationInboxPort notificationInboxPort;

    @Mock
    private DispatchLedgerPort dispatchLedger;

    @Mock
    private MessageRenderer messageRenderer;

    @Mock
    private NotificationMetricsPort notificationMetricsPort;

    @Mock
    private NotificationSenderPort sender;

    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void stubComposer() {
        lenient().when(messageRenderer.render(any(NotificationRequest.class))).thenReturn(RENDERED);
        lenient().when(notificationSenderFactory.forChannel(DistributionChannel.EMAIL)).thenReturn(sender);
    }

    @Test
    void shouldCompleteEventAndRecordSuccessMetricsWhenAllDeliveriesSucceed() {
        // Arrange
        DispatchInboxEventsService service = newService(3, Duration.ZERO);
        claims(entry("event-ref-1", "idem-1"));
        singleCustomerWithTargets(TARGET);

        // Act
        int processed = service.dispatchPendingEvents(10);

        // Assert
        assertEquals(1, processed);
        verify(notificationInboxPort).markStatus("event-ref-1", NotificationStatus.COMPLETED);
        verify(notificationInboxPort, never()).markFailed(anyString(), anyString());
        verify(notificationInboxPort).renewLease("event-ref-1");
        verify(notificationMetricsPort).incrementEventsProcessed();
        verify(notificationMetricsPort).incrementCustomersNotified();
        verify(notificationMetricsPort).recordDeliverySucceeded(eq(DistributionChannel.EMAIL), anyLong(), eq(false));
        verify(dispatchLedger).recordAttempt(new DispatchAttempt(
                "idem-1", "customer-1", TARGET, 1, DispatchOutcome.DELIVERED, null));
    }

    @Test
    void shouldClaimEventsOneAtATimeUntilInboxIsEmpty() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        when(notificationInboxPort.claimNext()).thenReturn(
                Optional.of(entry("event-ref-a", "idem-a")),
                Optional.of(entry("event-ref-b", "idem-b")),
                Optional.empty()
        );
        singleCustomerWithTargets(TARGET);

        // Act
        int processed = service.dispatchPendingEvents(10);

        // Assert
        assertEquals(2, processed);
        verify(notificationInboxPort, times(3)).claimNext();
        verify(notificationInboxPort).markStatus("event-ref-a", NotificationStatus.COMPLETED);
        verify(notificationInboxPort).markStatus("event-ref-b", NotificationStatus.COMPLETED);
    }

    @Test
    void shouldStopClaimingWhenBatchSizeIsReached() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        when(notificationInboxPort.claimNext()).thenReturn(
                Optional.of(entry("event-ref-a", "idem-a")),
                Optional.of(entry("event-ref-b", "idem-b"))
        );
        singleCustomerWithTargets(TARGET);

        // Act
        int processed = service.dispatchPendingEvents(2);

        // Assert
        assertEquals(2, processed);
        verify(notificationInboxPort, times(2)).claimNext();
    }

    @Test
    void shouldSkipTargetsAlreadyDeliveredAndRenewLeaseOnlyAfterRealAttempts() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        DistributionListTarget delivered = new DistributionListTarget(DistributionChannel.EMAIL, "done@example.com");
        claims(entry("event-ref-2", "idem-2"));
        singleCustomerWithTargets(delivered, TARGET);
        when(dispatchLedger.isDelivered("idem-2", "customer-1", delivered)).thenReturn(true);

        // Act
        int processed = service.dispatchPendingEvents(1);

        // Assert
        assertEquals(1, processed);
        ArgumentCaptor<NotificationDispatchRequest> requestCaptor = ArgumentCaptor.forClass(NotificationDispatchRequest.class);
        verify(sender).send(requestCaptor.capture());
        assertEquals(TARGET, requestCaptor.getValue().target());
        verify(notificationInboxPort, times(1)).renewLease("event-ref-2");
        verify(notificationInboxPort).markStatus("event-ref-2", NotificationStatus.COMPLETED);
    }

    @Test
    void shouldCompleteWithoutNotifyingAgainWhenEveryTargetWasAlreadyDelivered() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        claims(entry("event-ref-2b", "idem-2b"));
        singleCustomerWithTargets(TARGET);
        when(dispatchLedger.isDelivered("idem-2b", "customer-1", TARGET)).thenReturn(true);

        // Act
        service.dispatchPendingEvents(1);

        // Assert
        verifyNoInteractions(sender);
        verify(notificationInboxPort).markStatus("event-ref-2b", NotificationStatus.COMPLETED);
        verify(notificationMetricsPort, never()).incrementCustomersNotified();
    }

    @Test
    void shouldSkipCustomerAndCountItWhenCustomerHasNoDistributionList() {
        // Arrange
        DispatchInboxEventsService service = newService(2, Duration.ZERO);
        claims(entry("event-ref-3", "idem-3"));
        when(distributionListPort.findTargets("customer-1")).thenReturn(List.of());

        // Act
        int processed = service.dispatchPendingEvents(1);

        // Assert
        assertEquals(1, processed);
        verify(notificationInboxPort).markStatus("event-ref-3", NotificationStatus.COMPLETED);
        verify(notificationMetricsPort).incrementEventsProcessed();
        verify(notificationMetricsPort).incrementCustomersSkippedWithoutDistributionList();
        verify(notificationMetricsPort, never()).incrementCustomersNotified();
        verify(notificationSenderFactory, never()).forChannel(any());
        verifyNoInteractions(dispatchLedger);
    }

    @Test
    void shouldStillNotifyOtherCustomersWhenOneCustomerHasNoDistributionList() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        claims(entry("event-ref-3b", "idem-3b", Set.of("customer-1", "customer-2")));
        when(distributionListPort.findTargets("customer-1")).thenReturn(List.of());
        when(distributionListPort.findTargets("customer-2")).thenReturn(List.of(TARGET));

        // Act
        service.dispatchPendingEvents(1);

        // Assert
        verify(notificationInboxPort).markStatus("event-ref-3b", NotificationStatus.COMPLETED);
        verify(notificationMetricsPort).incrementCustomersSkippedWithoutDistributionList();
        verify(notificationMetricsPort).incrementCustomersNotified();
        verify(sender).send(any());
    }

    @Test
    void shouldMarkEventFailedWithoutRetryWhenNonRetryableExceptionIsThrown() {
        // Arrange
        DispatchInboxEventsService service = newService(3, Duration.ZERO);
        claims(entry("event-ref-4", "idem-4"));
        singleCustomerWithTargets(TARGET);
        doThrow(new NonRetryableDeliveryException("permanent failure")).when(sender).send(any());

        // Act
        int processed = service.dispatchPendingEvents(1);

        // Assert
        assertEquals(1, processed);
        verify(sender, times(1)).send(any());
        verify(notificationInboxPort).markFailed("event-ref-4", "One or more recipient deliveries failed");
        verify(notificationMetricsPort).recordDeliveryFailed(eq(DistributionChannel.EMAIL), anyLong(), eq(false), eq(true));
        verify(dispatchLedger).recordAttempt(new DispatchAttempt(
                "idem-4", "customer-1", TARGET, 1, DispatchOutcome.FAILED, "permanent failure"));
    }

    @Test
    void shouldRetryTransientFailureAndCompleteWhenLaterAttemptSucceeds() {
        // Arrange
        DispatchInboxEventsService service = newService(2, Duration.ZERO);
        claims(entry("event-ref-5", "idem-5"));
        singleCustomerWithTargets(TARGET);
        doThrow(new RuntimeException("temporary outage")).doNothing().when(sender).send(any());

        // Act
        int processed = service.dispatchPendingEvents(1);

        // Assert
        assertEquals(1, processed);
        verify(notificationInboxPort).markStatus("event-ref-5", NotificationStatus.COMPLETED);
        verify(sender, times(2)).send(any());

        InOrder inOrder = inOrder(notificationMetricsPort, dispatchLedger);
        inOrder.verify(notificationMetricsPort).recordDeliveryFailed(eq(DistributionChannel.EMAIL), anyLong(), eq(false), eq(false));
        inOrder.verify(dispatchLedger).recordAttempt(new DispatchAttempt(
                "idem-5", "customer-1", TARGET, 1, DispatchOutcome.FAILED, "temporary outage"));
        inOrder.verify(notificationMetricsPort).recordDeliverySucceeded(eq(DistributionChannel.EMAIL), anyLong(), eq(true));
        inOrder.verify(dispatchLedger).recordAttempt(new DispatchAttempt(
                "idem-5", "customer-1", TARGET, 2, DispatchOutcome.DELIVERED, null));
    }

    @Test
    void shouldTreatUnclassifiedExceptionsAsTransient() {
        // Arrange
        DispatchInboxEventsService service = newService(3, Duration.ZERO);
        claims(entry("event-ref-11", "idem-11"));
        singleCustomerWithTargets(TARGET);
        doThrow(new IllegalStateException("mail sender missing")).when(sender).send(any());

        // Act
        service.dispatchPendingEvents(1);

        // Assert
        verify(sender, times(3)).send(any());
        verify(notificationMetricsPort, times(3)).recordDeliveryFailed(eq(DistributionChannel.EMAIL), anyLong(), anyBoolean(), eq(false));
        verify(notificationInboxPort).markFailed("event-ref-11", "One or more recipient deliveries failed");
    }

    @Test
    void shouldMarkEventFailedWhenTransientFailureExhaustsRetriesAndTruncateErrorDetails() {
        // Arrange
        DispatchInboxEventsService service = newService(2, Duration.ZERO);
        claims(entry("event-ref-6", "idem-6"));
        singleCustomerWithTargets(TARGET);
        doThrow(new RuntimeException("x".repeat(1200))).when(sender).send(any());

        // Act
        int processed = service.dispatchPendingEvents(1);

        // Assert
        assertEquals(1, processed);
        verify(notificationInboxPort).markFailed("event-ref-6", "One or more recipient deliveries failed");
        ArgumentCaptor<DispatchAttempt> attemptCaptor = ArgumentCaptor.forClass(DispatchAttempt.class);
        verify(dispatchLedger, times(2)).recordAttempt(attemptCaptor.capture());
        assertEquals(1000, attemptCaptor.getAllValues().get(0).errorDetails().length());
        assertEquals(1000, attemptCaptor.getAllValues().get(1).errorDetails().length());
    }

    @Test
    void shouldStoreUnknownErrorWhenDeliveryExceptionMessageIsBlank() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        claims(entry("event-ref-7", "idem-7"));
        singleCustomerWithTargets(TARGET);
        doThrow(new RuntimeException(" ")).when(sender).send(any());

        // Act
        service.dispatchPendingEvents(1);

        // Assert
        verify(dispatchLedger).recordAttempt(new DispatchAttempt(
                "idem-7", "customer-1", TARGET, 1, DispatchOutcome.FAILED, "unknown error"));
    }

    @Test
    void shouldMarkEventFailedAndNotCountItWhenCompositionFails() {
        // Arrange
        DispatchInboxEventsService service = newService(1, Duration.ZERO);
        claims(entry("event-ref-8", "idem-8"));
        when(messageRenderer.render(any())).thenThrow(new RuntimeException("composition failed"));

        // Act
        int processed = service.dispatchPendingEvents(1);

        // Assert
        assertEquals(0, processed);
        verify(notificationInboxPort).markFailed("event-ref-8", "composition failed");
        verify(notificationMetricsPort, never()).incrementEventsProcessed();
        verify(notificationInboxPort, never()).markStatus(anyString(), any());
        verify(dispatchLedger, never()).recordAttempt(any());
    }

    @Test
    void shouldBackOffExponentiallyWithBoundedJitterBetweenRetries() {
        // Arrange
        DispatchInboxEventsService service = newService(3, Duration.ofMillis(100));
        claims(entry("event-ref-9", "idem-9"));
        singleCustomerWithTargets(TARGET);
        doThrow(new RuntimeException("temporary outage")).when(sender).send(any());

        // Act
        service.dispatchPendingEvents(1);

        // Assert
        verify(sender, times(3)).send(any());
        assertEquals(2, sleeps.size());
        assertBetween(sleeps.get(0), 100, 150);
        assertBetween(sleeps.get(1), 200, 300);
    }

    @Test
    void shouldLeaveEventForLeaseExpiryWhenInterruptedDuringBackoff() {
        // Arrange
        DispatchInboxEventsService service = newService(2, Duration.ofMillis(100), duration -> {
            throw new InterruptedException();
        });
        claims(entry("event-ref-10", "idem-10"));
        singleCustomerWithTargets(TARGET, new DistributionListTarget(DistributionChannel.EMAIL, "other@example.com"));
        doThrow(new RuntimeException("temporary outage")).when(sender).send(any());

        try {
            // Act
            int processed = service.dispatchPendingEvents(10);

            // Assert
            assertEquals(0, processed);
            verify(sender, times(1)).send(any());
            verify(notificationInboxPort, times(1)).claimNext();
            verify(notificationInboxPort, never()).markFailed(anyString(), anyString());
            verify(notificationInboxPort, never()).markStatus(anyString(), any());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void claims(NotificationInboxEntry entry) {
        when(notificationInboxPort.claimNext()).thenReturn(Optional.of(entry), Optional.empty());
    }

    private void singleCustomerWithTargets(DistributionListTarget... targets) {
        when(distributionListPort.findTargets("customer-1")).thenReturn(List.of(targets));
    }

    private static void assertBetween(Duration actual, long minMillis, long maxMillis) {
        assertTrue(actual.toMillis() >= minMillis && actual.toMillis() <= maxMillis,
                () -> actual + " not within [" + minMillis + ", " + maxMillis + "] ms");
    }

    private DispatchInboxEventsService newService(int maxAttempts, Duration initialBackoff) {
        return newService(maxAttempts, initialBackoff, sleeps::add);
    }

    private DispatchInboxEventsService newService(int maxAttempts, Duration initialBackoff, BackoffSleeper sleeper) {
        TargetDispatcher targetDispatcher = new TargetDispatcher(
                notificationSenderFactory,
                dispatchLedger,
                notificationMetricsPort,
                new DispatchRetryPolicy(maxAttempts, initialBackoff),
                sleeper
        );
        return new DispatchInboxEventsService(
                distributionListPort,
                dispatchLedger,
                notificationInboxPort,
                messageRenderer,
                notificationMetricsPort,
                targetDispatcher
        );
    }

    private NotificationInboxEntry entry(String eventReference, String idempotencyKey) {
        return entry(eventReference, idempotencyKey, Set.of("customer-1"));
    }

    private NotificationInboxEntry entry(String eventReference, String idempotencyKey, Set<String> customers) {
        return new NotificationInboxEntry(eventReference, new NotificationRequest(
                idempotencyKey,
                new Site("Site A", "Address A"),
                customers,
                new RegulatoryEventPayload("EMA", "DOC_TYPE", LocalDate.of(2026, 10, 7), 75)
        ));
    }
}
