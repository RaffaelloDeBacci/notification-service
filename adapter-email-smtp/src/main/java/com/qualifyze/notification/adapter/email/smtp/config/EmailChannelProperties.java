package com.qualifyze.notification.adapter.email.smtp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification.channel.email")
public record EmailChannelProperties(
        @DefaultValue("no-reply@qualifyze.local") String from,
        @DefaultValue("localhost") String host,
        @DefaultValue("1025") int port,
        @DefaultValue("") String username,
        @DefaultValue("") String password,
        @DefaultValue("false") boolean auth,
        @DefaultValue("false") boolean starttls,
        // Connect, read and write timeout; bounds a hung SMTP server so one delivery
        // cannot outlive the inbox claim lease.
        @DefaultValue("10s") Duration timeout
) {
}
