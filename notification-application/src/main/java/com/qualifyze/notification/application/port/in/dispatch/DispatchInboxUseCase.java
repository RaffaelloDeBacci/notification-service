package com.qualifyze.notification.application.port.in.dispatch;

public interface DispatchInboxUseCase {
    int dispatchPendingEvents(int maxBatchSize);
}
