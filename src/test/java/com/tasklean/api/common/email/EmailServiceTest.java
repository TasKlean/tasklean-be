package com.tasklean.api.common.email;

import com.tasklean.api.common.exception.EmailDeliveryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM = "noreply@tasklean.test";

    @Mock
    private JavaMailSender mailSender;

    private EmailContent content(Delivery delivery) {
        return new EmailContent("Subject line", "Body text", delivery);
    }

    // --- dispatch ---

    @Test
    void send_putsContentAndConfiguredFromOnTheMessage() {
        EmailService service = new EmailService(mailSender, FROM);

        service.send("bob@example.com", content(Delivery.ADVISORY));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getSubject()).isEqualTo("Subject line");
        assertThat(message.getText()).isEqualTo("Body text");
        // Config-driven, not a constant: a real-delivery provider only accepts a verified sender.
        assertThat(message.getFrom()).isEqualTo(FROM);
    }

    // --- delivery policy ---

    @Test
    void send_advisoryAndProviderRefuses_isSwallowed() {
        EmailService service = new EmailService(mailSender, FROM);
        doThrow(new MailSendException("limit reached")).when(mailSender).send(any(SimpleMailMessage.class));

        // The operation an advisory email reports already happened, so a refusal must not become
        // its failure - that is what keeps a provider outage from breaking a sign-in.
        assertThatCode(() -> service.send("bob@example.com", content(Delivery.ADVISORY)))
                .doesNotThrowAnyException();
    }

    @Test
    void send_essentialAndProviderRefuses_throwsEmailDelivery() {
        EmailService service = new EmailService(mailSender, FROM);
        doThrow(new MailSendException("limit reached")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.send("bob@example.com", content(Delivery.ESSENTIAL)))
                .isInstanceOf(EmailDeliveryException.class);
    }
}
