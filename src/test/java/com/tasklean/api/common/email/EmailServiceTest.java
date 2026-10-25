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

    private static final String FROM = "noreply@tasklean.test";

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationEmail_buildsPrefilledLinkWithEncodedEmailAndCode() {
        // Trailing slash on the base URL must not produce a double slash in the link.
        EmailService service = new EmailService(mailSender, "http://localhost:3000/", FROM);

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

    @Test
    void sendRegistrationAttemptEmail_notifiesOwnerWithoutSecrets() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendRegistrationAttemptEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("account already exists");
    }

    @Test
    void sendPasswordResetEmail_buildsResetLinkWithToken() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendPasswordResetEmail("bob@example.com", "tok-ABC123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText())
                .contains("http://localhost:3000/reset-password?token=tok-ABC123")
                .contains("expires in 15 minutes");
    }

    @Test
    void sendGoogleOnlyResetEmail_mentionsGoogleAndSendsNoLink() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendGoogleOnlyResetEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("Google").doesNotContain("/reset-password?token=");
    }

    @Test
    void sendPasswordChangedEmail_notifiesOwnerOfChange() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendPasswordChangedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("password was just changed");
    }

    @Test
    void sendPasswordSetEmail_notifiesOwnerOfAddedPassword() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendPasswordSetEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("password was just added");
    }

    @Test
    void sendGoogleLinkedEmail_notifiesOwner() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendGoogleLinkedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("Google account was just linked");
    }

    @Test
    void sendGoogleUnlinkedEmail_notifiesOwner() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendGoogleUnlinkedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("Google sign-in was just removed");
    }

    @Test
    void sendAccountDeactivatedEmail_notifiesOwner() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendAccountDeactivatedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("account was just deactivated");
    }

    @Test
    void sendTwoFactorEnabledEmail_notifiesOwnerAndPointsAtRecoveryCodes() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendTwoFactorEnabledEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("Two-factor authentication was just turned on");
    }

    @Test
    void sendTwoFactorDisabledEmail_notifiesOwnerOfWeakenedSignIn() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendTwoFactorDisabledEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("just turned off");
    }

    @Test
    void sendRecoveryCodesRegeneratedEmail_notifiesOwnerPreviousCodesAreDead() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendRecoveryCodesRegeneratedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("previous codes no longer work");
    }

    @Test
    void sendRecoveryCodeUsedEmail_notifiesOwnerOfAppFreeSignIn() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendRecoveryCodeUsedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("recovery code");
    }

    @Test
    void sendNewSignInEmail_includesDeviceAndIp() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendNewSignInEmail("bob@example.com", "Chrome on Windows", "203.0.113.5");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("bob@example.com");
        assertThat(message.getText()).contains("Chrome on Windows").contains("203.0.113.5");
    }

    @Test
    void sendNewSignInEmail_blankIp_omitsIpClause() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendNewSignInEmail("bob@example.com", "Chrome on Windows", null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getText()).contains("Chrome on Windows").doesNotContain("IP ");
    }

    @Test
    void send_setsTheConfiguredFromAddress() {
        EmailService service = new EmailService(mailSender, "http://localhost:3000", FROM);

        service.sendPasswordChangedEmail("bob@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        // Config-driven, not a constant: a real-delivery provider only accepts a verified sender.
        assertThat(captor.getValue().getFrom()).isEqualTo(FROM);
    }
}
