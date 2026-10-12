package com.qualifyze.notification.application.usecase.dispatch;

import com.qualifyze.notification.application.port.out.notification.NotificationSenderPort;
import com.qualifyze.notification.application.port.out.notification.NonRetryableDeliveryException;
import com.qualifyze.notification.domain.model.DistributionChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationSenderFactoryTest {

    @Test
    void shouldReturnMatchingSenderWhenChannelExists() {
        // Arrange
        NotificationSenderPort emailSender = mock(NotificationSenderPort.class);
        NotificationSenderPort slackSender = mock(NotificationSenderPort.class);
        when(emailSender.supportedChannel()).thenReturn(DistributionChannel.EMAIL);
        when(slackSender.supportedChannel()).thenReturn(DistributionChannel.SLACK);
        NotificationSenderFactory factory = new NotificationSenderFactory(List.of(emailSender, slackSender));

        // Act
        NotificationSenderPort result = factory.forChannel(DistributionChannel.EMAIL);

        // Assert
        assertSame(emailSender, result);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenMultipleSendersConfiguredForSameChannel() {
        // Arrange
        NotificationSenderPort firstEmailSender = mock(NotificationSenderPort.class);
        NotificationSenderPort secondEmailSender = mock(NotificationSenderPort.class);
        when(firstEmailSender.supportedChannel()).thenReturn(DistributionChannel.EMAIL);
        when(secondEmailSender.supportedChannel()).thenReturn(DistributionChannel.EMAIL);

        // Act
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new NotificationSenderFactory(
                List.of(firstEmailSender, secondEmailSender)
        ));

        // Assert
        assertEquals("Multiple senders configured for channel: EMAIL", exception.getMessage());
    }

    @Test
    void shouldThrowNonRetryableDeliveryExceptionWhenChannelIsNotConfigured() {
        // Arrange
        NotificationSenderPort emailSender = mock(NotificationSenderPort.class);
        when(emailSender.supportedChannel()).thenReturn(DistributionChannel.EMAIL);
        NotificationSenderFactory factory = new NotificationSenderFactory(List.of(emailSender));

        // Act
        NonRetryableDeliveryException exception = assertThrows(
                NonRetryableDeliveryException.class,
                () -> factory.forChannel(DistributionChannel.SLACK)
        );

        // Assert
        assertEquals("No sender configured for channel: SLACK", exception.getMessage());
    }
}
