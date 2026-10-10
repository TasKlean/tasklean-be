package com.tasklean.api.common.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationEmail_buildsPrefilledLinkWithEncodedEmailAndCode() {
        // Trailing slash on the base URL must not produce a double slash in the link.
        EmailService service = new EmailService(mailSender, "http://localhost:3000/");

        service.sendVerificationEmail("a+b@example.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        // Email is URL-encoded (+ → %2B, @ → %40); exact param names email/code.
        assertThat(message.getText())
                .contains("http://localhost:3000/verify-email?email=a%2Bb%40example.com&code=123456")
                .contains("enter this code manually: 123456");
        assertThat(message.getTo()).containsExactly("a+b@example.com");
    }
}
