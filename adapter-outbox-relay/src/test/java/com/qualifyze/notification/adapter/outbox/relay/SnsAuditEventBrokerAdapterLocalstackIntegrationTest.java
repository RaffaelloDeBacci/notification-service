package com.qualifyze.notification.adapter.outbox.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.application.port.out.audit.AuditEvent;
import com.qualifyze.notification.application.port.out.audit.SequencedAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers(disabledWithoutDocker = true)
class SnsAuditEventBrokerAdapterLocalstackIntegrationTest {

    @Container
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.0.3")
    ).withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    private SnsClient snsClient;
    private SqsClient sqsClient;
    private String topicArn;
    private String queueUrl;

    @BeforeEach
    void setUp() {
        snsClient = SnsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SNS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(),
                        LOCALSTACK.getSecretKey()
                )))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
        sqsClient = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(),
                        LOCALSTACK.getSecretKey()
                )))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();

        CreateTopicResponse createTopicResponse = snsClient.createTopic(builder -> builder.name("notification-audit-events"));
        topicArn = createTopicResponse.topicArn();

        CreateQueueResponse createQueueResponse = sqsClient.createQueue(builder -> builder.queueName("notification-audit-events"));
        queueUrl = createQueueResponse.queueUrl();

        String queueArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
        String queuePolicy = """
                {
                  "Version":"2012-10-17",
                  "Statement":[
                    {
                      "Sid":"AllowSnsPublish",
                      "Effect":"Allow",
                      "Principal":"*",
                      "Action":"sqs:SendMessage",
                      "Resource":"%s",
                      "Condition":{
                        "ArnEquals":{"aws:SourceArn":"%s"}
                      }
                    }
                  ]
                }
                """.formatted(queueArn, topicArn);
        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, queuePolicy))
                .build());

        snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(Map.of("RawMessageDelivery", "true"))
                .build());
    }

    @Test
    void shouldDeliverPublishedAuditEventToSqsViaSns() throws Exception {
        // Arrange
        SnsAuditEventBrokerAdapter adapter = new SnsAuditEventBrokerAdapter(
                snsClient,
                topicArn,
                "notification-service",
                "qualifyze-notification-service"
        );

        // Act
        adapter.publish(new SequencedAuditEvent(21L, new AuditEvent(
                "123e4567-e89b-12d3-a456-426614174000",
                "notification.delivery.attempt-recorded",
                "DispatchAttempt",
                "idem-21",
                "system",
                OffsetDateTime.parse("2026-10-08T12:34:56+02:00"),
                "{\"outcome\":\"DELIVERED\",\"attemptNumber\":1}"
        )));

        // Assert
        String body = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            var messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(1)
                            .waitTimeSeconds(1)
                            .build())
                    .messages();
            if (!messages.isEmpty()) {
                body = messages.getFirst().body();
                break;
            }
            Thread.sleep(200);
        }
        assertFalse(body == null || body.isBlank());
        JsonNode jsonNode = new ObjectMapper().readTree(body);
        assertEquals(21L, jsonNode.get("sequence").asLong());
        assertEquals("notification.delivery.attempt-recorded", jsonNode.get("eventType").asText());
        assertEquals("DELIVERED", jsonNode.get("payload").get("outcome").asText());
        assertEquals(1, jsonNode.get("payload").get("attemptNumber").asInt());
    }
}
