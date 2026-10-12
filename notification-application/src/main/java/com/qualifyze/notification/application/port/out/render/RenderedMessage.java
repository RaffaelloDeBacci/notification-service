package com.qualifyze.notification.application.port.out.render;

/**
 * Channel-neutral rendered content. Channel adapters shape it for their medium
 * (e.g. e-mail subject/body, Slack text), keeping the type × channel matrix linear.
 */
public record RenderedMessage(
        String subject,
        String body
) {
}
