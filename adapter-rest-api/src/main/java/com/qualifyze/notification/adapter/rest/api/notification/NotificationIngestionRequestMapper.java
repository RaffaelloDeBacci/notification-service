package com.qualifyze.notification.adapter.rest.api.notification;

import com.qualifyze.notification.adapter.rest.api.contract.model.CertificateExpiryIngestRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.IngestNotificationRequest;
import com.qualifyze.notification.adapter.rest.api.contract.model.RegulatoryEventIngestRequest;
import com.qualifyze.notification.domain.model.CertificateExpiryPayload;
import com.qualifyze.notification.domain.model.NotificationPayload;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Translates any incoming {@link IngestNotificationRequest} into the domain
 * {@link NotificationRequest}: envelope fields come from the base type, the payload
 * from the concrete variant selected by the {@code messageType} discriminator.
 */
@Component
public class NotificationIngestionRequestMapper {

    public NotificationRequest toRequest(IngestNotificationRequest request) {
        Objects.requireNonNull(request, "request is required");
        return new NotificationRequest(
                request.getIdempotencyKey(),
                new Site(request.getSiteName(), request.getSiteAddress()),
                request.getAffectedCustomerReferences(),
                toPayload(request)
        );
    }

    private static NotificationPayload toPayload(IngestNotificationRequest request) {
        // The generated DTO hierarchy is not sealed, hence the default branch.
        return switch (request) {
            case RegulatoryEventIngestRequest event -> new RegulatoryEventPayload(
                    event.getAuthorityName(),
                    event.getDocumentTypeCode(),
                    event.getIssueDate(),
                    event.getConfidenceLevel()
            );
            case CertificateExpiryIngestRequest certificate -> new CertificateExpiryPayload(
                    certificate.getCertificateType(),
                    certificate.getIssuingAuthority(),
                    certificate.getExpiryDate()
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported notification messageType: " + request.getMessageType());
        };
    }
}
