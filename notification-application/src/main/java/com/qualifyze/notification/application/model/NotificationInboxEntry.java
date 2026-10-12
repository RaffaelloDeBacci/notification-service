package com.qualifyze.notification.application.model;

import com.qualifyze.notification.domain.model.NotificationRequest;

public record NotificationInboxEntry(
        String eventReference,
        NotificationRequest request
) {
}
