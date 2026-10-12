package com.qualifyze.notification.adapter.scheduler;

import com.qualifyze.notification.application.port.in.dispatch.DispatchInboxUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxEventProcessingWorkerTest {

    @Mock
    private DispatchInboxUseCase dispatchInboxUseCase;

    @Test
    void shouldDispatchPendingEventsWithConfiguredBatchSizeWhenWorkerRuns() {
        // Arrange
        InboxEventProcessingWorker worker = new InboxEventProcessingWorker(dispatchInboxUseCase, 25);
        when(dispatchInboxUseCase.dispatchPendingEvents(25)).thenReturn(2);

        // Act
        worker.processInbox();

        // Assert
        verify(dispatchInboxUseCase).dispatchPendingEvents(25);
    }
}
