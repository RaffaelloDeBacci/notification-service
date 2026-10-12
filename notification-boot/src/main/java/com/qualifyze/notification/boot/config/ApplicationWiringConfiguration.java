package com.qualifyze.notification.boot.config;

import com.qualifyze.notification.application.usecase.dispatch.DispatchRetryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ApplicationWiringConfiguration {

    @Bean
    DispatchRetryPolicy dispatchRetryPolicy(
            @Value("${notification.dispatch.retry.max-attempts:3}") int maxAttempts,
            @Value("${notification.dispatch.retry.initial-backoff-ms:200}") long initialBackoffMillis
    ) {
        return new DispatchRetryPolicy(maxAttempts, Duration.ofMillis(initialBackoffMillis));
    }
}
