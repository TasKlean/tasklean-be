package com.tasklean.api.common.email;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * The one way out to the mail provider. Takes an already-composed {@link EmailContent} from
 * {@link EmailTemplates} and sends it, applying the failure policy the content declares — so no
 * caller can reach the provider directly or decide for itself what a refusal means.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends one composed message. A refusal either fails the caller or is swallowed, depending on
     * the content's {@link Delivery} — see {@link EmailTemplates} for why each template chose its.
     *
     * @param to      the recipient email address
     * @param content the composed subject, body and delivery policy
     * @throws EmailDeliveryException if the provider refuses an {@link Delivery#ESSENTIAL} message
     */
    public void send(String to, EmailContent content) {
        try {
            dispatch(to, content);
        } catch (MailException e) {
            // Subject only, never the body: bodies carry codes, links and device details.
            if (content.delivery() == Delivery.ESSENTIAL) {
                log.warn("Could not send essential email: subject={} cause={}",
                        content.subject(), e.getMessage());
                throw new EmailDeliveryException(ErrorMessages.EMAIL_DELIVERY_FAILED, e);
            }
            // The operation this reports has already happened, so losing the email must not undo
            // it. That is what keeps a provider outage from breaking a sign-in.
            log.warn("Could not send advisory email, continuing: subject={} cause={}",
                    content.subject(), e.getMessage());
        }
    }

    private void dispatch(String to, EmailContent content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(content.subject());
        message.setText(content.body());
        message.setFrom(fromAddress);
        mailSender.send(message);
    }
}
