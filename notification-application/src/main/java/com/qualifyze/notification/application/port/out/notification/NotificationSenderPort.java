package com.qualifyze.notification.application.port.out.notification;

import com.qualifyze.notification.application.usecase.dispatch.NotificationDispatchRequest;
import com.qualifyze.notification.domain.model.DistributionChannel;

public interface NotificationSenderPort {
    DistributionChannel supportedChannel();

    void send(NotificationDispatchRequest request);
}
