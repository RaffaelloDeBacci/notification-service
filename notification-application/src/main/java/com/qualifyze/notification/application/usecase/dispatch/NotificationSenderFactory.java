package com.qualifyze.notification.application.usecase.dispatch;

import com.qualifyze.notification.application.port.out.notification.NotificationSenderPort;
import com.qualifyze.notification.application.port.out.notification.NonRetryableDeliveryException;
import com.qualifyze.notification.domain.model.DistributionChannel;
import jakarta.inject.Named;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Named
public class NotificationSenderFactory {

    private final Map<DistributionChannel, NotificationSenderPort> sendersByChannel;

    public NotificationSenderFactory(List<NotificationSenderPort> notificationSenders) {
        EnumMap<DistributionChannel, NotificationSenderPort> senderMap = new EnumMap<>(DistributionChannel.class);
        for (NotificationSenderPort sender : notificationSenders) {
            DistributionChannel channel = sender.supportedChannel();
            NotificationSenderPort existingSender = senderMap.putIfAbsent(channel, sender);
            if (existingSender != null) {
                throw new IllegalStateException("Multiple senders configured for channel: " + channel);
            }
        }
        this.sendersByChannel = Map.copyOf(senderMap);
    }

    public NotificationSenderPort forChannel(DistributionChannel channel) {
        NotificationSenderPort sender = sendersByChannel.get(channel);
        if (sender == null) {
            throw new NonRetryableDeliveryException("No sender configured for channel: " + channel);
        }
        return sender;
    }
}
