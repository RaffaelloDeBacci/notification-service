package com.qualifyze.notification.application.port.out.render;

import com.qualifyze.notification.domain.model.NotificationRequest;

/**
 * Turns a notification into channel-neutral text. Wording, document-type labels and
 * date formats are presentation owned by this service and live behind this port so
 * they can be localised without touching the use cases.
 */
public interface MessageRenderer {

    RenderedMessage render(NotificationRequest request);
}
