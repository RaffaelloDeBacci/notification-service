package com.qualifyze.notification.adapter.scheduler;

import com.qualifyze.notification.application.port.in.dispatch.DispatchInboxUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.inbox.worker.enabled", havingValue = "true", matchIfMissing = true)
public class InboxEventProcessingWorker {

    private final DispatchInboxUseCase dispatchInboxUseCase;
    private final int batchSize;

    public InboxEventProcessingWorker(
            DispatchInboxUseCase dispatchInboxUseCase,
            @Value("${notification.inbox.worker.batch-size:50}") int batchSize
    ) {
        this.dispatchInboxUseCase = dispatchInboxUseCase;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.inbox.worker-delay-ms:1000}")
    public void processInbox() {
        dispatchInboxUseCase.dispatchPendingEvents(batchSize);
    }
}
