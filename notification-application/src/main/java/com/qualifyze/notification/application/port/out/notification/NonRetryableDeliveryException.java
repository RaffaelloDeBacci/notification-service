package com.qualifyze.notification.application.port.out.notification;

/**
 * Thrown by a {@link NotificationSenderPort} when a delivery cannot succeed on retry
 * (e.g. authentication or malformed message). Any other exception is treated as transient.
 */
public class NonRetryableDeliveryException extends RuntimeException {

    public NonRetryableDeliveryException(String message) {
        super(message);
    }

    public NonRetryableDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
