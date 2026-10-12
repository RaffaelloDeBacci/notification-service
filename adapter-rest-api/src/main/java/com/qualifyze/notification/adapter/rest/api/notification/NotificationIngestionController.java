package com.qualifyze.notification.adapter.rest.api.notification;

import com.qualifyze.notification.adapter.rest.api.contract.NotificationIngestionApi;
import com.qualifyze.notification.adapter.rest.api.contract.model.IngestNotificationRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.ProcessNotificationResponse;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.port.in.ingest.IngestNotificationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationIngestionController implements NotificationIngestionApi {

    private final IngestNotificationUseCase ingestNotificationUseCase;
    private final NotificationIngestionRequestMapper notificationIngestionRequestMapper;

    public NotificationIngestionController(
            IngestNotificationUseCase ingestNotificationUseCase,
            NotificationIngestionRequestMapper notificationIngestionRequestMapper
    ) {
        this.ingestNotificationUseCase = ingestNotificationUseCase;
        this.notificationIngestionRequestMapper = notificationIngestionRequestMapper;
    }

    /**
     * Design note (polymorphic request body):
     *
     * <p>The request body is modelled with a discriminator on the base schema
     * {@code IngestNotificationRequest}; {@code messageType} selects the concrete
     * variant, which Jackson deserializes via generated
     * {@code @JsonTypeInfo}/{@code @JsonSubTypes}. This keeps a single stable
     * controller parameter regardless of how many message types exist.
     *
     * <p>Adding a messageType is therefore <b>not</b> a new endpoint and needs no
     * change here: it is a new schema + enum value + discriminator mapping in the
     * contract, plus one {@code case} in {@link NotificationIngestionRequestMapper}.
     *
     * <p>({@code oneOf} was evaluated for its slightly cleaner Swagger variant
     * dropdown, but openapi-generator mis-generates {@code oneOf} + {@code allOf} +
     * discriminator once two or more members share a base, so the discriminator-on-base
     * form is used instead — see the note in {@code paths/notifications.yaml}.)
     */
    @Override
    public ResponseEntity<ProcessNotificationResponse> ingestNotification(IngestNotificationRequest request) {
        NotificationInboxUpsertResult result = ingestNotificationUseCase.ingest(
                notificationIngestionRequestMapper.toRequest(request)
        );

        return ResponseEntity.accepted().body(
                new ProcessNotificationResponse()
                        .eventReference(result.eventReference())
                        .idempotencyKey(result.idempotencyKey())
                        .messageType(request.getMessageType())
                        .duplicate(result.duplicate())
                        .status(ProcessNotificationResponse.StatusEnum.valueOf(result.status().name()))
        );
    }
}
