package com.tasklean.api.common.email;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.email.html.EmailLayout;
import com.tasklean.api.common.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * The one way out to the mail provider. Takes an already-composed {@link EmailContent} from
 * {@link EmailTemplates}, sends it as HTML with a plain-text fallback, and applies the failure
 * policy the content declares — so no caller can reach the provider directly or decide for itself
 * what a refusal means.
 */
@Slf4j
@Service
public class EmailService {

    // Attached rather than linked: an inline part renders without the reader allowing remote
    // images, and keeps the mark out of any URL we would then have to keep alive.
    private static final Resource LOGO = new ClassPathResource("email/logo.png");
    private static final String LOGO_CONTENT_TYPE = "image/png";

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
     * @param content the composed subject, both body parts and the delivery policy
     * @throws EmailDeliveryException if the provider refuses an {@link Delivery#ESSENTIAL} message
     */
    public void send(String to, EmailContent content) {
        try {
            dispatch(to, content);
        } catch (MailException | MessagingException e) {
            // Subject only, never a body: bodies carry codes, links and device details.
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

    private void dispatch(String to, EmailContent content) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
        helper.setTo(to);
        helper.setFrom(fromAddress);
        helper.setSubject(content.subject());
        // Both parts, text first: that order is what makes it multipart/alternative, so a client
        // that will not render HTML still has a readable message.
        helper.setText(content.text(), content.html());
        // After setText, never before - an inline part can only attach to a body that exists.
        helper.addInline(EmailLayout.LOGO_CID, LOGO, LOGO_CONTENT_TYPE);
        mailSender.send(message);
    }
}
