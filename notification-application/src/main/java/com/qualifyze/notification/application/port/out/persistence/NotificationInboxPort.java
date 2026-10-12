package com.qualifyze.notification.application.port.out.persistence;

import com.qualifyze.notification.application.model.NotificationInboxEntry;
import com.qualifyze.notification.application.model.NotificationInboxUpsertResult;
import com.qualifyze.notification.application.model.NotificationStatus;
import com.qualifyze.notification.domain.model.NotificationRequest;

import java.util.Optional;

public interface NotificationInboxPort {
    NotificationInboxUpsertResult upsert(NotificationRequest request);

    /**
     * Claims the next event to dispatch under a time-boxed lease: an event whose lease
     * expires (e.g. the worker crashed) becomes claimable again.
     */
    Optional<NotificationInboxEntry> claimNext();

    /** Extends the lease of an event that is still being dispatched. */
    void renewLease(String eventReference);

    void markStatus(String eventReference, NotificationStatus status);

    void markFailed(String eventReference, String reason);
}
