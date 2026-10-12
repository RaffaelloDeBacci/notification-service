package com.qualifyze.notification.adapter.rest.api.notification;

import com.qualifyze.notification.adapter.rest.api.contract.model.MessageType;
import com.qualifyze.notification.adapter.rest.api.contract.model.ProcessNotificationResponse;
import com.qualifyze.notification.adapter.rest.api.contract.model.RegulatoryEventIngestRequest;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.in.ingest.IngestNotificationUseCase;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationIngestionControllerTest {

    @Mock
    private IngestNotificationUseCase ingestNotificationUseCase;

    @Mock
    private NotificationIngestionRequestMapper notificationIngestionRequestMapper;

    @InjectMocks
    private NotificationIngestionController controller;

    @Test
    void shouldReturnAcceptedResponseWhenNotificationIsIngestedSuccessfully() {
        // Arrange
        RegulatoryEventIngestRequest request = request("idem-1");
        NotificationRequest mapped = mappedRequest("idem-1");
        when(notificationIngestionRequestMapper.toRequest(request)).thenReturn(mapped);
        when(ingestNotificationUseCase.ingest(mapped)).thenReturn(new NotificationInboxUpsertResult(
                "event-ref-1",
                "idem-1",
                NotificationStatus.RECEIVED,
                false
        ));

        // Act
        ResponseEntity<ProcessNotificationResponse> response = controller.ingestNotification(request);

        // Assert
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("event-ref-1", response.getBody().getEventReference());
        assertEquals("idem-1", response.getBody().getIdempotencyKey());
        assertEquals(MessageType.REGULATORY_EVENT, response.getBody().getMessageType());
        assertEquals(false, response.getBody().getDuplicate());
        assertEquals(ProcessNotificationResponse.StatusEnum.RECEIVED, response.getBody().getStatus());
    }

    @Test
    void shouldPreserveDuplicateFlagWhenUseCaseMarksNotificationAsDuplicate() {
        // Arrange
        RegulatoryEventIngestRequest request = request("idem-dup");
        NotificationRequest mapped = mappedRequest("idem-dup");
        when(notificationIngestionRequestMapper.toRequest(request)).thenReturn(mapped);
        when(ingestNotificationUseCase.ingest(mapped)).thenReturn(new NotificationInboxUpsertResult(
                "event-ref-2",
                "idem-dup",
                NotificationStatus.RECEIVED,
                true
        ));

        // Act
        ResponseEntity<ProcessNotificationResponse> response = controller.ingestNotification(request);

        // Assert
        assertEquals(true, response.getBody().getDuplicate());
    }

    private static RegulatoryEventIngestRequest request(String idempotencyKey) {
        RegulatoryEventIngestRequest request = new RegulatoryEventIngestRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setMessageType(MessageType.REGULATORY_EVENT);
        return request;
    }

    private static NotificationRequest mappedRequest(String idempotencyKey) {
        return new NotificationRequest(
                idempotencyKey,
                new Site("Site A", "Address A"),
                Set.of("customer-1"),
                new RegulatoryEventPayload("EMA", "DOC", LocalDate.of(2026, 10, 7), 88)
        );
    }
}
