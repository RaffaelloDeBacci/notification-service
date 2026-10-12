package com.qualifyze.notification.application.usecase.ingestion;

import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.port.in.ingest.IngestNotificationUseCase;
import com.qualifyze.notification.application.port.out.persistence.NotificationInboxPort;
import com.qualifyze.notification.domain.model.NotificationRequest;
import jakarta.inject.Named;

@Named
public class IngestNotificationService implements IngestNotificationUseCase {

    private final NotificationInboxPort notificationInboxPort;

    public IngestNotificationService(NotificationInboxPort notificationInboxPort) {
        this.notificationInboxPort = notificationInboxPort;
    }

    @Override
    public NotificationInboxUpsertResult ingest(NotificationRequest request) {
        return notificationInboxPort.upsert(request);
    }
}
