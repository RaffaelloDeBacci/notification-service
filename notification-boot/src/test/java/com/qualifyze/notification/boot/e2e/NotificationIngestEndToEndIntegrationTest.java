package com.qualifyze.notification.boot.e2e;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualifyze.notification.domain.model.DistributionChannel;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(NotificationIngestEndToEndIntegrationTest.TestMailConfiguration.class)
class NotificationIngestEndToEndIntegrationTest {

    private static final String RESOURCE_SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String TOPIC_NAME = "notification-audit-events-e2e-" + RESOURCE_SUFFIX;
    private static final String QUEUE_NAME = "notification-audit-queue-e2e-" + RESOURCE_SUFFIX;
    private static final String EVENT_ENDPOINT = "/api/v1/notifications";
    private static final String EMAIL_SUBJECT = "Regulatory event detected";
    private static final String EVENT_STATUS_RECEIVED = "RECEIVED";
    private static final String EVENT_STATUS_COMPLETED = "COMPLETED";
    private static final String AUDIT_EVENT_TYPE = "notification.delivery.attempt-recorded";
    private static final String AUDIT_AGGREGATE_TYPE = "DispatchAttempt";
    private static final String AUDIT_SOURCE_SERVICE = "qualifyze-notification-service";
    private static final String AUDIT_OUTCOME = "DELIVERED";
    private static final int AWAIT_POLL_INTERVAL_MILLIS = 100;
    private static final int POLL_INTERVAL_MILLIS = 200;
    private static final int SQS_MAX_MESSAGES = 10;
    private static final int SQS_POLL_WAIT_SECONDS = 1;
    private static final Duration MAIL_WAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration SQS_WAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Object TOPOLOGY_LOCK = new Object();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.0.3")
    ).withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryJavaMailSender inMemoryJavaMailSender;

    @LocalServerPort
    private int port;

    private static volatile String topicArn;
    private static volatile String queueUrl;
    private static volatile String queueArn;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRESQL_CONTAINER::getDriverClassName);
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("liquibase.duplicateFileMode", () -> "WARN");
        registry.add("notification.audit.broker", () -> "sns");
        registry.add("notification.audit.aws.region", LOCALSTACK::getRegion);
        registry.add("notification.audit.aws.endpoint", () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SNS).toString());
        registry.add("notification.audit.aws.access-key", LOCALSTACK::getAccessKey);
        registry.add("notification.audit.aws.secret-key", LOCALSTACK::getSecretKey);
        registry.add("notification.audit.sns.topic-arn", NotificationIngestEndToEndIntegrationTest::topicArn);
        registry.add("notification.inbox.worker.batch-size", () -> "1");
        registry.add("notification.inbox.worker-delay-ms", () -> "100");
        registry.add("notification.audit.outbox.worker.batch-size", () -> "1");
        registry.add("notification.audit.outbox.worker-delay-ms", () -> "100");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM dispatch_ledger");
        jdbcTemplate.update("DELETE FROM audit_outbox");
        jdbcTemplate.update("DELETE FROM notification_inbox");
        jdbcTemplate.update("DELETE FROM distribution_list");
        inMemoryJavaMailSender.clear();
        ensureAuditTopology();
        drainQueue();
    }

    @Test
    void shouldIngestNotificationSendEmailAndRelayAuditEventToSqs() throws Exception {
        TestData data = testData();

        // Arrange
        jdbcTemplate.update(
                "INSERT INTO distribution_list (customer_reference, delivery_channel, notification_target) VALUES (?, ?, ?)",
                data.customerReference(),
                DistributionChannel.EMAIL.name(),
                data.recipient()
        );

        // Act
        HttpResponse<String> response = postJson(EVENT_ENDPOINT, regulatoryEventRequestBody(data));

        // Assert
        IngestResponse responseBody = OBJECT_MAPPER.readValue(response.body(), IngestResponse.class);
        assertAcceptedResponse(response, responseBody);
        String eventReference = responseBody.eventReference();

        awaitEventCompletion(eventReference);
        awaitAuditRelayCompletion(data.idempotencyKey());

        SimpleMailMessage sentMessage = awaitSentEmail();
        assertEmailDeliveredToRecipient(sentMessage, data.recipient());

        AuditEvent receivedMessage = awaitAuditMessage("notification.received", SQS_WAIT_TIMEOUT);
        assertEquals(eventReference, receivedMessage.aggregateId());

        AuditEvent attemptMessage = awaitAuditMessage(AUDIT_EVENT_TYPE, SQS_WAIT_TIMEOUT);
        assertAuditMessage(attemptMessage, data);
    }

    private String regulatoryEventRequestBody(TestData data) {
        return """
                {
                  "messageType": "REGULATORY_EVENT",
                  "idempotencyKey": "%s",
                  "siteName": "Site E2E",
                  "siteAddress": "Address E2E",
                  "authorityName": "EMA",
                  "documentTypeCode": "OAI",
                  "issueDate": "2026-10-07",
                  "confidenceLevel": 91,
                  "affectedCustomerReferences": ["%s"]
                }
                """.formatted(data.idempotencyKey(), data.customerReference());
    }

    private static void assertAcceptedResponse(HttpResponse<String> response, IngestResponse responseBody) {
        assertAll(
                () -> assertEquals(202, response.statusCode()),
                () -> assertEquals(EVENT_STATUS_RECEIVED, responseBody.status()),
                () -> assertFalse(responseBody.duplicate()),
                () -> assertTrue(responseBody.eventReference() != null && !responseBody.eventReference().isBlank())
        );
    }

    private SimpleMailMessage awaitSentEmail() throws InterruptedException {
        await(() -> inMemoryJavaMailSender.sentMessages().size() == 1, MAIL_WAIT_TIMEOUT);
        return inMemoryJavaMailSender.sentMessages().getFirst();
    }

    private static void assertEmailDeliveredToRecipient(SimpleMailMessage sentMessage, String recipient) {
        assertAll(
                () -> assertEquals(recipient, sentMessage.getTo()[0]),
                () -> assertEquals(EMAIL_SUBJECT, sentMessage.getSubject())
        );
    }

    private void awaitEventCompletion(String eventReference) throws InterruptedException {
        await(() -> EVENT_STATUS_COMPLETED.equals(jdbcTemplate.query(
                "SELECT status FROM notification_inbox WHERE event_reference = ?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                eventReference
        )), MAIL_WAIT_TIMEOUT);
    }

    private void awaitAuditRelayCompletion(String idempotencyKey) throws InterruptedException {
        await(() -> Boolean.TRUE.equals(jdbcTemplate.query(
                """
                        SELECT published_at IS NOT NULL
                        FROM audit_outbox
                        WHERE aggregate_id = ? AND event_type = ?
                        """,
                resultSet -> resultSet.next() ? resultSet.getBoolean(1) : null,
                idempotencyKey,
                AUDIT_EVENT_TYPE
        )), SQS_WAIT_TIMEOUT);
    }

    private static void assertAuditMessage(AuditEvent auditBody, TestData data) {
        assertAll(
                () -> assertEquals(AUDIT_EVENT_TYPE, auditBody.eventType()),
                () -> assertEquals(AUDIT_AGGREGATE_TYPE, auditBody.aggregateType()),
                () -> assertEquals(data.idempotencyKey(), auditBody.aggregateId()),
                () -> assertEquals(AUDIT_SOURCE_SERVICE, auditBody.sourceService()),
                () -> assertEquals(data.customerReference(), auditBody.payload().customerReference()),
                () -> assertEquals(AUDIT_OUTCOME, auditBody.payload().outcome()),
                () -> assertEquals(data.recipient(), auditBody.payload().notificationTarget())
        );
    }

    private static TestData testData() {
        return TestData.create(RESOURCE_SUFFIX);
    }

    private static String topicArn() {
        ensureAuditTopology();
        return topicArn;
    }

    private static void ensureAuditTopology() {
        if (topicArn != null && queueUrl != null && queueArn != null) {
            return;
        }
        synchronized (TOPOLOGY_LOCK) {
            if (topicArn != null && queueUrl != null && queueArn != null) {
                return;
            }
            try (SnsClient snsClient = buildSnsClient(); SqsClient sqsClient = buildSqsClient()) {
                CreateTopicResponse createTopicResponse = snsClient.createTopic(builder -> builder.name(TOPIC_NAME));
                topicArn = createTopicResponse.topicArn();

                CreateQueueResponse createQueueResponse = sqsClient.createQueue(builder -> builder.queueName(QUEUE_NAME));
                queueUrl = createQueueResponse.queueUrl();

                queueArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
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
        }
    }

    private static SnsClient buildSnsClient() {
        return SnsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SNS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(),
                        LOCALSTACK.getSecretKey()
                )))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
    }

    private static SqsClient buildSqsClient() {
        return SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(),
                        LOCALSTACK.getSecretKey()
                )))
                .region(Region.of(LOCALSTACK.getRegion()))
                .build();
    }

    private void drainQueue() {
        if (queueUrl == null) {
            return;
        }
        try (SqsClient sqsClient = buildSqsClient()) {
            while (true) {
                List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(SQS_MAX_MESSAGES)
                                .waitTimeSeconds(SQS_POLL_WAIT_SECONDS)
                                .build())
                        .messages();
                if (messages.isEmpty()) {
                    return;
                }
                for (Message message : messages) {
                    sqsClient.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                }
            }
        }
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Consumes queued audit messages until one of the given type arrives; others are discarded. */
    private AuditEvent awaitAuditMessage(String eventType, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (SqsClient sqsClient = buildSqsClient()) {
            while (System.nanoTime() < deadline) {
                List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(SQS_POLL_WAIT_SECONDS)
                                .build())
                        .messages();
                if (messages.isEmpty()) {
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                    continue;
                }
                Message message = messages.getFirst();
                sqsClient.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                AuditEvent auditEvent = OBJECT_MAPPER.readValue(message.body(), AuditEvent.class);
                if (eventType.equals(auditEvent.eventType())) {
                    return auditEvent;
                }
            }
        }
        throw new AssertionError("Timed out waiting for audit message " + eventType + " on SQS");
    }

    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(AWAIT_POLL_INTERVAL_MILLIS);
        }
        throw new AssertionError("Timed out waiting for condition");
    }

    @TestConfiguration
    static class TestMailConfiguration {

        @Bean
        @Primary
        InMemoryJavaMailSender javaMailSender() {
            return new InMemoryJavaMailSender();
        }
    }

    static class InMemoryJavaMailSender implements JavaMailSender {

        private final List<SimpleMailMessage> sentMessages = new CopyOnWriteArrayList<>();

        List<SimpleMailMessage> sentMessages() {
            return sentMessages;
        }

        void clear() {
            sentMessages.clear();
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getDefaultInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getDefaultInstance(new Properties()), contentStream);
            } catch (Exception exception) {
                throw new MailException("Unable to create MimeMessage") {
                };
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            sentMessages.add(new SimpleMailMessage(simpleMessage));
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            for (SimpleMailMessage simpleMessage : simpleMessages) {
                sentMessages.add(new SimpleMailMessage(simpleMessage));
            }
        }
    }

    private record TestData(String customerReference, String recipient, String idempotencyKey) {

        private static TestData create(String suffix) {
            return new TestData(
                    "customer-e2e-" + suffix,
                    "e2e-" + suffix + "@example.com",
                    "idem-e2e-" + suffix
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IngestResponse(String status, boolean duplicate, String eventReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuditEvent(String eventType, String aggregateType, String aggregateId, String sourceService, AuditPayload payload) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuditPayload(String customerReference, String outcome, String notificationTarget) {
    }
}
