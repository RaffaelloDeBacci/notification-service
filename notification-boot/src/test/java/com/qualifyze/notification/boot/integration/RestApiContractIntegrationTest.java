package com.qualifyze.notification.boot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestApiContractIntegrationTest extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnValidationErrorWhenEventsPayloadIsMalformed() throws Exception {
        // Arrange
        HttpResponse<String> response = postJson("/api/v1/notifications", "{");

        // Act
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertEquals("NOTIF-VAL-002", body.get("code").asText());
        assertEquals("VALIDATION_FAILED", body.get("reason").asText());
    }

    @Test
    void shouldReturnValidationErrorWhenRequiredEventFieldsAreMissing() throws Exception {
        // Arrange
        HttpResponse<String> response = postJson("/api/v1/notifications", """
                {
                  "messageType": "REGULATORY_EVENT",
                  "idempotencyKey": "idem-rest-1"
                }
                """);
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertEquals("NOTIF-VAL-001", body.get("code").asText());
        assertEquals("VALIDATION_FAILED", body.get("reason").asText());
        assertTrue(body.get("details").isArray());
        assertTrue(body.get("details").size() > 0);
    }

    @Test
    void shouldAcceptValidEventPayloadAndReturnContractCompliantResponse() throws Exception {
        // Arrange
        HttpResponse<String> response = postJson("/api/v1/notifications", """
                {
                  "messageType": "REGULATORY_EVENT",
                  "idempotencyKey": "idem-rest-2",
                  "siteName": "Site Rest",
                  "siteAddress": "Address Rest",
                  "authorityName": "EMA",
                  "documentTypeCode": "OAI",
                  "issueDate": "2026-10-07",
                  "confidenceLevel": 88,
                  "affectedCustomerReferences": ["customer-rest-1"]
                }
                """);
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.ACCEPTED.value(), response.statusCode());
        assertEquals("idem-rest-2", body.get("idempotencyKey").asText());
        assertEquals("REGULATORY_EVENT", body.get("messageType").asText());
        assertEquals("RECEIVED", body.get("status").asText());
        assertFalse(body.get("duplicate").asBoolean());
        assertTrue(body.get("eventReference").asText().length() > 0);
    }

    @Test
    void shouldExposeActuatorMetricsEndpoint() throws Exception {
        // Arrange
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/actuator/metrics")))
                .GET()
                .build();

        // Act
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.get("names").isArray());
        assertTrue(body.get("names").size() > 0);
    }

    @Test
    void shouldReturnNotFoundForMissingEndpoint() throws Exception {
        // Arrange
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/missing")))
                .GET()
                .build();

        // Act
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
        assertEquals("NOTIF-RES-404", body.get("code").asText());
        assertEquals("NOT_FOUND", body.get("reason").asText());
        assertEquals("Endpoint not found", body.get("message").asText());
    }

    @Test
    void shouldUpsertDistributionListThroughHttpAndReturnSortedTargets() throws Exception {
        // Arrange
        HttpResponse<String> response = putJson("/api/v1/customers/customer-rest-2/distribution-list", """
                {
                  "targets": [
                    {
                      "deliveryChannel": "SLACK",
                      "notificationTarget": "#ops-alerts"
                    },
                    {
                      "deliveryChannel": "EMAIL",
                      "notificationTarget": "a@example.com"
                    },
                    {
                      "deliveryChannel": "EMAIL",
                      "notificationTarget": "z@example.com"
                    }
                  ]
                }
                """);
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertEquals("customer-rest-2", body.get("customerReference").asText());
        assertEquals("EMAIL", body.get("targets").get(0).get("deliveryChannel").asText());
        assertEquals("a@example.com", body.get("targets").get(0).get("notificationTarget").asText());
        assertEquals("EMAIL", body.get("targets").get(1).get("deliveryChannel").asText());
        assertEquals("z@example.com", body.get("targets").get(1).get("notificationTarget").asText());
        assertEquals("SLACK", body.get("targets").get(2).get("deliveryChannel").asText());
        assertEquals("#ops-alerts", body.get("targets").get(2).get("notificationTarget").asText());
    }

    @Test
    void shouldAcceptIdempotencyKeyAtMaximumLength() throws Exception {
        // Arrange + Act
        HttpResponse<String> response = postJson("/api/v1/notifications", regulatoryEvent("k".repeat(255)));

        // Assert
        assertEquals(HttpStatus.ACCEPTED.value(), response.statusCode());
    }

    @Test
    void shouldRejectIdempotencyKeyOneCharacterOverMaximumLength() throws Exception {
        // Arrange + Act
        HttpResponse<String> response = postJson("/api/v1/notifications", regulatoryEvent("k".repeat(256)));
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertEquals("NOTIF-VAL-001", body.get("code").asText());
    }

    @Test
    void shouldAcceptCustomerReferenceAtMaximumLengthInDistributionListPath() throws Exception {
        // Arrange
        String customerReference = "c".repeat(64);

        // Act
        HttpResponse<String> response = putJson(distributionListPath(customerReference), targets(1));

        // Assert
        assertEquals(HttpStatus.OK.value(), response.statusCode());
    }

    @Test
    void shouldRecordActorHeaderInDistributionListAuditEvents() throws Exception {
        // Act
        HttpResponse<String> response = putJson(
                distributionListPath("customer-rest-actor"),
                singleEmailTarget("ops@example.com"),
                "X-Actor", "jane.doe@qualifyze.com"
        );

        // Assert
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertEquals("jane.doe@qualifyze.com", jdbcTemplate.queryForObject(
                "SELECT actor FROM audit_outbox WHERE aggregate_id = ?",
                String.class,
                "customer-rest-actor"
        ));
    }

    @Test
    void shouldRejectCustomerReferenceOneCharacterOverMaximumLengthInDistributionListPath() throws Exception {
        // Arrange
        String customerReference = "c".repeat(65);

        // Act
        HttpResponse<String> response = putJson(distributionListPath(customerReference), targets(1));
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertEquals("NOTIF-VAL-001", body.get("code").asText());
    }

    @Test
    void shouldAcceptDistributionListWithMaximumNumberOfTargets() throws Exception {
        // Arrange + Act
        HttpResponse<String> response = putJson(distributionListPath("customer-max-targets"), targets(50));

        // Assert
        assertEquals(HttpStatus.OK.value(), response.statusCode());
    }

    @Test
    void shouldRejectDistributionListWithOneTargetOverMaximum() throws Exception {
        // Arrange + Act
        HttpResponse<String> response = putJson(distributionListPath("customer-too-many-targets"), targets(51));
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertEquals("NOTIF-VAL-001", body.get("code").asText());
    }

    @Test
    void shouldAcceptNotificationTargetAtMaximumLength() throws Exception {
        // Arrange
        String domain = "@example.com";
        String target = "a".repeat(320 - domain.length()) + domain;

        // Act
        HttpResponse<String> response = putJson(distributionListPath("customer-long-target"), singleEmailTarget(target));

        // Assert
        assertEquals(HttpStatus.OK.value(), response.statusCode());
    }

    @Test
    void shouldRejectNotificationTargetOneCharacterOverMaximumLength() throws Exception {
        // Arrange
        String domain = "@example.com";
        String target = "a".repeat(321 - domain.length()) + domain;

        // Act
        HttpResponse<String> response = putJson(distributionListPath("customer-too-long-target"), singleEmailTarget(target));
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
        assertEquals("NOTIF-VAL-001", body.get("code").asText());
    }

    @Test
    void shouldRejectInvalidEmailTargetAsBusinessRuleViolation() throws Exception {
        // Arrange + Act
        HttpResponse<String> response = putJson(distributionListPath("customer-bad-email"), singleEmailTarget("not-an-email"));
        JsonNode body = objectMapper.readTree(response.body());

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.statusCode());
        assertEquals("NOTIF-BIZ-001", body.get("code").asText());
    }

    private static String regulatoryEvent(String idempotencyKey) {
        return """
                {
                  "messageType": "REGULATORY_EVENT",
                  "idempotencyKey": "%s",
                  "siteName": "Site Rest",
                  "siteAddress": "Address Rest",
                  "authorityName": "EMA",
                  "documentTypeCode": "OAI",
                  "issueDate": "2026-10-07",
                  "confidenceLevel": 88,
                  "affectedCustomerReferences": ["customer-rest-1"]
                }
                """.formatted(idempotencyKey);
    }

    private static String distributionListPath(String customerReference) {
        return "/api/v1/customers/" + customerReference + "/distribution-list";
    }

    private static String targets(int count) {
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                targets.append(',');
            }
            targets.append("{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"ops-%d@example.com\"}".formatted(i));
        }
        return "{\"targets\":[" + targets + "]}";
    }

    private static String singleEmailTarget(String target) {
        return "{\"targets\":[{\"deliveryChannel\":\"EMAIL\",\"notificationTarget\":\"%s\"}]}".formatted(target);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putJson(String path, String body, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) {
            builder.headers(headers);
        }
        HttpRequest request = builder.build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
