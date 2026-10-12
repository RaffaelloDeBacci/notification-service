package com.qualifyze.notification.domain.model;

import java.time.LocalDate;

/** A regulatory document (e.g. an FDA warning letter) detected for the notification's site. */
public record RegulatoryEventPayload(
        String authorityName,
        String documentTypeCode,
        LocalDate issueDate,
        int confidenceLevel
) implements NotificationPayload {
    public RegulatoryEventPayload {
        authorityName = Require.text(authorityName, "authorityName");
        documentTypeCode = Require.text(documentTypeCode, "documentTypeCode");
        Require.present(issueDate, "issueDate");
        Require.range(confidenceLevel, 0, 100, "confidenceLevel");
    }

    @Override
    public MessageType messageType() {
        return MessageType.REGULATORY_EVENT;
    }
}
