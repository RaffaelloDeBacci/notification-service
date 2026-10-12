package com.qualifyze.notification.adapter.email.smtp;

import com.qualifyze.notification.adapter.email.smtp.config.EmailChannelProperties;
import com.qualifyze.notification.application.port.out.notification.NonRetryableDeliveryException;
import com.qualifyze.notification.application.usecase.dispatch.NotificationDispatchRequest;
import com.qualifyze.notification.domain.model.DistributionChannel;
import com.qualifyze.notification.domain.model.DistributionListTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpNotificationSenderAdapterTest {

    private static final NotificationDispatchRequest REQUEST = new NotificationDispatchRequest(
            new DistributionListTarget(DistributionChannel.EMAIL, "ops@example.com"),
            "Alert",
            "Body content"
    );

    @Mock
    private JavaMailSender javaMailSender;

    private SmtpNotificationSenderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SmtpNotificationSenderAdapter(
                javaMailSender,
                new EmailChannelProperties(
                        "no-reply@example.com",
                        "localhost",
                        1025,
                        "",
                        "",
                        false,
                        false,
                        Duration.ofSeconds(10)
                )
        );
    }

    @Test
    void shouldSendEmailWhenRequestUsesSupportedChannel() {
        // Act
        adapter.send(REQUEST);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("no-reply@example.com", sentMessage.getFrom());
        assertEquals("ops@example.com", sentMessage.getTo()[0]);
        assertEquals("Alert", sentMessage.getSubject());
        assertEquals("Body content", sentMessage.getText());
    }

    @Test
    void shouldClassifyAuthenticationFailureAsNonRetryable() {
        // Arrange
        doThrow(new MailAuthenticationException("bad credentials")).when(javaMailSender).send(any(SimpleMailMessage.class));

        // Act + Assert
        assertThrows(NonRetryableDeliveryException.class, () -> adapter.send(REQUEST));
    }

    @Test
    void shouldClassifyParseFailureAsNonRetryable() {
        // Arrange
        doThrow(new MailParseException("malformed")).when(javaMailSender).send(any(SimpleMailMessage.class));

        // Act + Assert
        assertThrows(NonRetryableDeliveryException.class, () -> adapter.send(REQUEST));
    }

    @Test
    void shouldLeaveSendFailureTransient() {
        // Arrange
        doThrow(new MailSendException("server unavailable")).when(javaMailSender).send(any(SimpleMailMessage.class));

        // Act + Assert
        assertThrows(MailSendException.class, () -> adapter.send(REQUEST));
    }
}
