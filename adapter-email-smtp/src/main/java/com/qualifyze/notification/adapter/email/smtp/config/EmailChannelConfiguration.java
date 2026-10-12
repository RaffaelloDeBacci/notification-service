package com.qualifyze.notification.adapter.email.smtp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(EmailChannelProperties.class)
public class EmailChannelConfiguration {

    @Bean
    JavaMailSender smtpJavaMailSender(EmailChannelProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());
        mailSender.setUsername(properties.username());
        mailSender.setPassword(properties.password());

        Properties mailProperties = mailSender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", String.valueOf(properties.auth()));
        mailProperties.put("mail.smtp.starttls.enable", String.valueOf(properties.starttls()));
        String timeoutMillis = String.valueOf(properties.timeout().toMillis());
        mailProperties.put("mail.smtp.connectiontimeout", timeoutMillis);
        mailProperties.put("mail.smtp.timeout", timeoutMillis);
        mailProperties.put("mail.smtp.writetimeout", timeoutMillis);

        return mailSender;
    }
}
