package com.qualifyze.notification.adapter.persistence.postgres;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qualifyze.notification.domain.model.NotificationPayload;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.Site;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * Serializes a {@link NotificationRequest} (minus its idempotency key, which has its
 * own column) to/from the {@code jsonb} stored in {@code notification_inbox.payload}:
 *
 * <pre>{"site": {...}, "affectedCustomerReferences": [...], "details": {"@type": "RegulatoryEventPayload", ...}}</pre>
 *
 * <p>The inbox is deliberately schema-on-read: it never queries payload fields,
 * so every message type shares one row shape and adding a type needs no DB
 * migration and no new entity. The {@code @type} discriminator is the payload's
 * simple class name and the sealed hierarchy's permitted subtypes are
 * auto-registered, so a new {@code NotificationPayload} subtype needs no code here.
 */
@Component
public class NotificationPayloadJsonCodec {

    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
    private interface NotificationPayloadMixin {
    }

    private record StoredNotification(
            Site site,
            Set<String> affectedCustomerReferences,
            NotificationPayload details
    ) {
    }

    private final ObjectMapper objectMapper;

    public NotificationPayloadJsonCodec() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addMixIn(NotificationPayload.class, NotificationPayloadMixin.class);
        this.objectMapper.registerSubtypes(NotificationPayload.class.getPermittedSubclasses());
    }

    public String toJson(NotificationRequest request) {
        try {
            return objectMapper.writeValueAsString(new StoredNotification(
                    request.site(),
                    new TreeSet<>(request.affectedCustomerReferences()),
                    request.payload()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize notification", exception);
        }
    }

    public NotificationRequest fromJson(String idempotencyKey, String json) {
        try {
            StoredNotification stored = objectMapper.readValue(json, StoredNotification.class);
            return new NotificationRequest(
                    idempotencyKey,
                    stored.site(),
                    stored.affectedCustomerReferences(),
                    stored.details()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize notification", exception);
        }
    }
}
