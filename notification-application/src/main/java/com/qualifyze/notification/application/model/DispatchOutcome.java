package com.qualifyze.notification.application.model;

/**
 * The outcome of a single delivery attempt. Whether a failed attempt was retried is
 * captured by the attempt number; the failure cause by the error details.
 */
public enum DispatchOutcome {
    DELIVERED,
    FAILED
}
