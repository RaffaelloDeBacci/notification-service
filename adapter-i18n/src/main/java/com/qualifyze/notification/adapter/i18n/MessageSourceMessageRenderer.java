package com.qualifyze.notification.adapter.i18n;

import com.qualifyze.notification.application.port.out.render.MessageRenderer;
import com.qualifyze.notification.application.port.out.render.RenderedMessage;
import com.qualifyze.notification.domain.model.CertificateExpiryPayload;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders notifications from the {@code classpath:i18n/messages} bundle (templates,
 * document-type labels, date format). The exhaustive {@code switch} over the sealed
 * payload makes the compiler the registry: a new message type without a {@code case}
 * does not compile.
 *
 * <p>Recipients have no locale yet, so everything renders in {@link #LOCALE}.
 */
@Component
public class MessageSourceMessageRenderer implements MessageRenderer {

    static final Locale LOCALE = Locale.ENGLISH;
    private static final String BASENAME = "classpath:i18n/messages";
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSourceMessageRenderer.class);

    private final MessageSource messages;

    public MessageSourceMessageRenderer() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename(BASENAME);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        this.messages = source;
    }

    @Override
    public RenderedMessage render(NotificationRequest request) {
        Site site = request.site();
        return switch (request.payload()) {
            case RegulatoryEventPayload event -> render(
                    "regulatory-event",
                    site.name(),
                    site.address(),
                    event.authorityName(),
                    documentTypeLabel(event.documentTypeCode()),
                    formatDate(event.issueDate()),
                    Integer.toString(event.confidenceLevel())
            );
            case CertificateExpiryPayload certificate -> render(
                    "certificate-expiry",
                    certificate.certificateType(),
                    site.name(),
                    site.address(),
                    certificate.issuingAuthority(),
                    formatDate(certificate.expiryDate())
            );
        };
    }

    // Arguments are pre-formatted strings so MessageFormat never applies its own number/date formatting.
    private RenderedMessage render(String templateKey, String... arguments) {
        return new RenderedMessage(
                messages.getMessage(templateKey + ".subject", null, LOCALE),
                messages.getMessage(templateKey + ".body", arguments, LOCALE)
        );
    }

    private String documentTypeLabel(String documentTypeCode) {
        String code = documentTypeCode.strip().toUpperCase(Locale.ROOT);
        String label = messages.getMessage("document-type." + code, null, null, LOCALE);
        if (label == null || label.isBlank()) {
            LOGGER.warn("No document type label configured for code {}", code);
            return code.replace('_', ' ').toLowerCase(Locale.ROOT);
        }
        return label;
    }

    private String formatDate(LocalDate date) {
        return DateTimeFormatter.ofPattern(messages.getMessage("date.format", null, LOCALE), LOCALE).format(date);
    }
}
