package com.qualifyze.notification.application.usecase.dispatch;

import com.qualifyze.notification.application.model.NotificationInboxEntry;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.in.dispatch.DispatchInboxUseCase;
import com.qualifyze.notification.application.port.out.observability.NotificationMetricsPort;
import com.qualifyze.notification.application.port.out.persistence.DispatchLedgerPort;
import com.qualifyze.notification.application.port.out.persistence.DistributionListPort;
import com.qualifyze.notification.application.port.out.persistence.NotificationInboxPort;
import com.qualifyze.notification.application.port.out.render.MessageRenderer;
import com.qualifyze.notification.application.port.out.render.RenderedMessage;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import com.qualifyze.notification.domain.model.NotificationRequest;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Named
public class DispatchInboxEventsService implements DispatchInboxUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchInboxEventsService.class);

    private final DistributionListPort distributionListPort;
    private final DispatchLedgerPort dispatchLedgerPort;
    private final NotificationInboxPort notificationInboxPort;
    private final MessageRenderer messageRenderer;
    private final NotificationMetricsPort notificationMetricsPort;
    private final TargetDispatcher targetDispatcher;

    public DispatchInboxEventsService(
            DistributionListPort distributionListPort,
            DispatchLedgerPort dispatchLedgerPort,
            NotificationInboxPort notificationInboxPort,
            MessageRenderer messageRenderer,
            NotificationMetricsPort notificationMetricsPort,
            TargetDispatcher targetDispatcher
    ) {
        this.distributionListPort = distributionListPort;
        this.dispatchLedgerPort = dispatchLedgerPort;
        this.notificationInboxPort = notificationInboxPort;
        this.messageRenderer = messageRenderer;
        this.notificationMetricsPort = notificationMetricsPort;
        this.targetDispatcher = targetDispatcher;
    }

    @Override
    public int dispatchPendingEvents(int maxBatchSize) {
        int processedEvents = 0;
        for (int claimed = 0; claimed < maxBatchSize && !Thread.currentThread().isInterrupted(); claimed++) {
            Optional<NotificationInboxEntry> inboxEntry = notificationInboxPort.claimNext();
            if (inboxEntry.isEmpty()) {
                break;
            }
            if (dispatchClaimed(inboxEntry.get())) {
                processedEvents++;
            }
        }
        return processedEvents;
    }

    /** Returns whether the event reached a final status (COMPLETED or FAILED by delivery). */
    private boolean dispatchClaimed(NotificationInboxEntry inboxEntry) {
        try {
            boolean hasFailures = dispatchNotification(inboxEntry);
            if (Thread.currentThread().isInterrupted()) {
                // Shutting down mid-event: leave it DISPATCHING so the lease expires and
                // another worker resumes it, skipping targets already in the ledger.
                LOGGER.info("Dispatch of {} interrupted; leaving it for lease expiry", inboxEntry.eventReference());
                return false;
            }
            if (hasFailures) {
                notificationInboxPort.markFailed(inboxEntry.eventReference(), "One or more recipient deliveries failed");
            } else {
                notificationInboxPort.markStatus(inboxEntry.eventReference(), NotificationStatus.COMPLETED);
            }
            notificationMetricsPort.incrementEventsProcessed();
            return true;
        } catch (Exception exception) {
            LOGGER.error("Dispatch failed for inbox entry {}", inboxEntry.eventReference(), exception);
            notificationInboxPort.markFailed(inboxEntry.eventReference(), exception.getMessage());
            return false;
        }
    }

    private boolean dispatchNotification(NotificationInboxEntry inboxEntry) {
        NotificationRequest request = inboxEntry.request();
        boolean hasFailures = false;
        RenderedMessage renderedMessage = messageRenderer.render(request);
        for (String customerReference : request.affectedCustomerReferences()) {
            List<DistributionListTarget> targets = distributionListPort.findTargets(customerReference);
            if (targets.isEmpty()) {
                LOGGER.warn(
                        "Skipping customer {} for notification {}: no distribution list configured",
                        customerReference,
                        request.idempotencyKey()
                );
                notificationMetricsPort.incrementCustomersSkippedWithoutDistributionList();
                continue;
            }
            boolean customerNotified = false;
            for (DistributionListTarget target : targets) {
                if (Thread.currentThread().isInterrupted()) {
                    return hasFailures;
                }
                if (dispatchLedgerPort.isDelivered(request.idempotencyKey(), customerReference, target)) {
                    LOGGER.debug("Skipping {} target for {}: already delivered", target.channel(), request.idempotencyKey());
                    continue;
                }
                boolean delivered = targetDispatcher.deliver(
                        request.idempotencyKey(),
                        customerReference,
                        target,
                        renderedMessage
                );
                notificationInboxPort.renewLease(inboxEntry.eventReference());
                if (delivered) {
                    customerNotified = true;
                } else {
                    hasFailures = true;
                }
            }
            if (customerNotified) {
                notificationMetricsPort.incrementCustomersNotified();
            }
        }
        return hasFailures;
    }
}
