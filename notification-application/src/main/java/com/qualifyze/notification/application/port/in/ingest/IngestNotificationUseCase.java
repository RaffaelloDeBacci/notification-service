package com.qualifyze.notification.application.port.in.ingest;

import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.domain.model.NotificationRequest;

public interface IngestNotificationUseCase {
    NotificationInboxUpsertResult ingest(NotificationRequest request);
}
