package com.qualifyze.notification.boot.config;

import com.qualifyze.notification.application.usecase.dispatch.DispatchRetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationWiringConfigurationTest {

    private final ApplicationWiringConfiguration configuration = new ApplicationWiringConfiguration();

    @Test
    void shouldCreateDispatchRetryPolicyWhenInputValuesAreValid() {
        // Arrange + Act
        DispatchRetryPolicy policy = configuration.dispatchRetryPolicy(3, 200L);

        // Assert
        assertEquals(3, policy.maxAttempts());
        assertEquals(Duration.ofMillis(200), policy.initialBackoff());
    }

    @Test
    void shouldAllowBoundaryValuesForDispatchRetryPolicy() {
        // Arrange + Act
        DispatchRetryPolicy policy = configuration.dispatchRetryPolicy(1, 0L);

        // Assert
        assertEquals(1, policy.maxAttempts());
        assertEquals(Duration.ZERO, policy.initialBackoff());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenRetryPolicyIsInvalid() {
        // Arrange + Act + Assert
        assertThrows(IllegalArgumentException.class, () -> configuration.dispatchRetryPolicy(0, 200L));
        assertThrows(IllegalArgumentException.class, () -> configuration.dispatchRetryPolicy(2, -1L));
    }
}
