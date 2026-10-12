package com.qualifyze.notification.adapter.outbox.relay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

import java.net.URI;

@Configuration
public class AwsAuditBrokerConfiguration {

    @Bean
    @ConditionalOnProperty(name = "notification.audit.broker", havingValue = "sns")
    SnsClient snsClient(
            @Value("${notification.audit.aws.region:us-east-1}") String region,
            @Value("${notification.audit.aws.endpoint:}") String endpoint,
            @Value("${notification.audit.aws.access-key:}") String accessKey,
            @Value("${notification.audit.aws.secret-key:}") String secretKey
    ) {
        SnsClientBuilder builder = SnsClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder = builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            );
        } else {
            builder = builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
