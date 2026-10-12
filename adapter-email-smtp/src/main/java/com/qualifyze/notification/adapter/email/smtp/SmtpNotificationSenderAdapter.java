package com.qualifyze.notification.adapter.email.smtp;

import com.qualifyze.notification.adapter.email.smtp.config.EmailChannelProperties;
import com.qualifyze.notification.application.port.out.notification.NonRetryableDeliveryException;
import com.qualifyze.notification.application.port.out.notification.NotificationSenderPort;
import com.qualifyze.notification.application.usecase.dispatch.NotificationDispatchRequest;
import com.qualifyze.notification.domain.model.DistributionChannel;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpNotificationSenderAdapter implements NotificationSenderPort {

    private final JavaMailSender javaMailSender;
    private final String senderAddress;

    public SmtpNotificationSenderAdapter(
            JavaMailSender javaMailSender,
            EmailChannelProperties properties
    ) {
        this.javaMailSender = javaMailSender;
        this.senderAddress = properties.from();
    }

    @Override
    public DistributionChannel supportedChannel() {
        return DistributionChannel.EMAIL;
    }

    @Override
    public void send(NotificationDispatchRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(request.target().destination());
        message.setSubject(request.subject());
        message.setText(request.message());
        try {
            javaMailSender.send(message);
        } catch (MailAuthenticationException | MailParseException exception) {
            // Wrong credentials or a malformed message cannot succeed on retry;
            // MailSendException (server unavailable, timeouts) stays transient.
            throw new NonRetryableDeliveryException(exception.getMessage(), exception);
        }
    }
}
