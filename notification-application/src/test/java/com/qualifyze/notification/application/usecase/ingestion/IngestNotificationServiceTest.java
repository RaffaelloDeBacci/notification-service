package com.qualifyze.notification.application.usecase.ingestion;

import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.application.port.out.persistence.NotificationInboxPort;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestNotificationServiceTest {

    @Mock
    private NotificationInboxPort notificationInboxPort;

    @InjectMocks
    private IngestNotificationService service;

    @Captor
    private ArgumentCaptor<NotificationRequest> requestCaptor;

    @Test
    void shouldDelegateRequestToInboxAndReturnUpsertResult() {
        // Arrange
        NotificationRequest request = new NotificationRequest(
                "idem-1",
                new Site("Plant A", "Street 1"),
                Set.of("customer-1", "customer-2"),
                new RegulatoryEventPayload("EMA", "RECALL_NOTICE", LocalDate.of(2026, 10, 7), 90)
        );
        NotificationInboxUpsertResult expected = new NotificationInboxUpsertResult(
                "event-ref-1",
                "idem-1",
                NotificationStatus.RECEIVED,
                false
        );
        when(notificationInboxPort.upsert(requestCaptor.capture())).thenReturn(expected);

        // Act
        NotificationInboxUpsertResult result = service.ingest(request);

        // Assert
        assertSame(request, requestCaptor.getValue());
        assertEquals(expected, result);
    }
}
