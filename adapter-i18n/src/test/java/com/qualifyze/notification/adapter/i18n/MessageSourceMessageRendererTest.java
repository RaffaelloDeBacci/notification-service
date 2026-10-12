package com.qualifyze.notification.adapter.i18n;

import com.qualifyze.notification.application.port.out.render.RenderedMessage;
import com.qualifyze.notification.domain.model.CertificateExpiryPayload;
import com.qualifyze.notification.domain.model.NotificationPayload;
import com.qualifyze.notification.domain.model.NotificationRequest;
import com.qualifyze.notification.domain.model.RegulatoryEventPayload;
import com.qualifyze.notification.domain.model.Site;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageSourceMessageRendererTest {

    private final MessageSourceMessageRenderer renderer = new MessageSourceMessageRenderer();

    @Test
    void shouldReproduceTheBriefSampleCharacterForCharacter() {
        // Arrange
        NotificationRequest request = request(
                new Site("Qualifyze Spain", "Passeig de Gracia 19 Barcelona"),
                new RegulatoryEventPayload("FDA", "WARNING_LETTER", LocalDate.of(2026, 6, 12), 80)
        );

        // Act
        RenderedMessage rendered = renderer.render(request);

        // Assert
        assertEquals("Regulatory event detected", rendered.subject());
        assertEquals(
                "Site Qualifyze Spain, located at Passeig de Gracia 19 Barcelona received a FDA warning letter on 12 June 2026. [80%]",
                rendered.body()
        );
    }

    @Test
    void shouldFormatSingleDigitDayWithoutLeadingZeroAndFallBackToHumanisedCode() {
        // Arrange
        NotificationRequest request = request(
                new Site("Madrid Plant", "Gran Via 1"),
                new RegulatoryEventPayload("ANSM", "PRODUCT_ALERT", LocalDate.of(2026, 1, 5), 87)
        );

        // Act
        RenderedMessage rendered = renderer.render(request);

        // Assert
        assertEquals(
                "Site Madrid Plant, located at Gran Via 1 received a ANSM product alert on 5 January 2026. [87%]",
                rendered.body()
        );
    }

    @Test
    void shouldResolveLabelForLowerCaseCode() {
        // Arrange
        NotificationRequest request = request(
                new Site("Site A", "Address A"),
                new RegulatoryEventPayload("FDA", "oai", LocalDate.of(2026, 3, 1), 100)
        );

        // Act
        RenderedMessage rendered = renderer.render(request);

        // Assert
        assertEquals(
                "Site Site A, located at Address A received a FDA official action indicated on 1 March 2026. [100%]",
                rendered.body()
        );
    }

    @Test
    void shouldInsertCallerTextVerbatimEvenWithMessageFormatCharacters() {
        // Arrange
        NotificationRequest request = request(
                new Site("O'Brien {Lab}", "1'000 Main St"),
                new RegulatoryEventPayload("FDA", "OAI", LocalDate.of(2026, 3, 1), 9)
        );

        // Act
        RenderedMessage rendered = renderer.render(request);

        // Assert
        assertEquals(
                "Site O'Brien {Lab}, located at 1'000 Main St received a FDA official action indicated on 1 March 2026. [9%]",
                rendered.body()
        );
    }

    @Test
    void shouldRenderCertificateExpiry() {
        // Arrange
        NotificationRequest request = request(
                new Site("Plant 3", "Baker Street 1"),
                new CertificateExpiryPayload("GMP_CERTIFICATE", "EMA", LocalDate.of(2026, 12, 31))
        );

        // Act
        RenderedMessage rendered = renderer.render(request);

        // Assert
        assertEquals("Certificate expiring soon", rendered.subject());
        assertEquals(
                "The GMP_CERTIFICATE certificate for Plant 3, located at Baker Street 1, issued by EMA expires on 31 December 2026.",
                rendered.body()
        );
    }

    private static NotificationRequest request(Site site, NotificationPayload payload) {
        return new NotificationRequest("idem-1", site, Set.of("customer-1"), payload);
    }
}
