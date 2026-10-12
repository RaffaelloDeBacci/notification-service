package com.qualifyze.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lives in the root package so Spring's default component, entity and repository
 * scanning covers every module ({@code domain}, {@code application}, {@code adapter.*}).
 */
@SpringBootApplication
@EnableScheduling
public class QualifyzeNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QualifyzeNotificationServiceApplication.class, args);
    }
}
