package com.tasklean.api.common.email;

import com.tasklean.api.common.email.html.EmailLayout;
import com.tasklean.api.common.exception.EmailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM = "noreply@tasklean.test";

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, FROM);
    }

    /** A mock returns null, and the helper needs a real message to assemble into. */
    private void givenAMessageToAssembleInto() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private EmailContent content(Delivery delivery) {
        return new EmailContent("Subject line", "Body text", "<p>Body text</p>", delivery);
    }

    private MimeMessage captureSent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    // --- assembly ---

    @Test
    void send_setsRecipientSubjectAndConfiguredFrom() throws Exception {
        givenAMessageToAssembleInto();
        service.send("bob@example.com", content(Delivery.ADVISORY));

        MimeMessage message = captureSent();
        assertThat(message.getAllRecipients()).hasSize(1);
        assertThat(message.getAllRecipients()[0]).hasToString("bob@example.com");
        assertThat(message.getSubject()).isEqualTo("Subject line");
        // Config-driven, not a constant: a real-delivery provider only accepts a verified sender.
        assertThat(message.getFrom()[0]).hasToString(FROM);
    }

    @Test
    void send_buildsHtmlWithAPlainTextAlternative() throws Exception {
        givenAMessageToAssembleInto();
        service.send("bob@example.com", content(Delivery.ADVISORY));

        MimeMessage message = captureSent();
        MimeMultipart root = (MimeMultipart) message.getContent();
        // The text part is what a client that will not render HTML falls back to, so losing it
        // would leave those readers an empty message.
        assertThat(flatten(root)).contains("Body text", "<p>Body text</p>");
    }

    @Test
    void send_attachesTheLogoInline() throws Exception {
        givenAMessageToAssembleInto();
        service.send("bob@example.com", content(Delivery.ADVISORY));

        MimeMessage message = captureSent();
        assertThat(contentIds((MimeMultipart) message.getContent()))
                .contains("<" + EmailLayout.LOGO_CID + ">");
    }

    @Test
    void logoResource_isOnTheClasspath() {
        // Every email attaches it, so a build that lost the asset would break all of them.
        assertThat(new ClassPathResource("email/logo.png").exists()).isTrue();
    }

    // --- delivery policy ---

    @Test
    void send_advisoryAndProviderRefuses_isSwallowed() {
        givenAMessageToAssembleInto();
        doThrow(new MailSendException("limit reached")).when(mailSender).send(any(MimeMessage.class));

        // The operation an advisory email reports already happened, so a refusal must not become
        // its failure - that is what keeps a provider outage from breaking a sign-in.
        assertThatCode(() -> service.send("bob@example.com", content(Delivery.ADVISORY)))
                .doesNotThrowAnyException();
    }

    @Test
    void send_essentialAndProviderRefuses_throwsEmailDelivery() {
        givenAMessageToAssembleInto();
        doThrow(new MailSendException("limit reached")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send("bob@example.com", content(Delivery.ESSENTIAL)))
                .isInstanceOf(EmailDeliveryException.class);
    }

    /** Collects every text leaf of the message tree. */
    private List<String> flatten(MimeMultipart multipart) throws Exception {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            Object content = multipart.getBodyPart(i).getContent();
            if (content instanceof MimeMultipart nested) {
                parts.addAll(flatten(nested));
            } else if (content instanceof String text) {
                parts.add(text);
            }
        }
        return parts;
    }

    /** Collects the content id of every part that has one. */
    private List<String> contentIds(MimeMultipart multipart) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            String[] header = multipart.getBodyPart(i).getHeader("Content-ID");
            if (header != null) {
                ids.addAll(List.of(header));
            }
            if (multipart.getBodyPart(i).getContent() instanceof MimeMultipart nested) {
                ids.addAll(contentIds(nested));
            }
        }
        return ids;
    }
}
