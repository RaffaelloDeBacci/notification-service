package com.qualifyze.notification.application.usecase.dispatch;

import com.qualifyze.notification.domain.model.DistributionListTarget;

public record NotificationDispatchRequest(
        DistributionListTarget target,
        String subject,
        String message
) {
}
