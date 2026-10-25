package com.tasklean.api.auth.passwordreset;

import com.tasklean.api.auth.passwordreset.dto.ForgotPasswordRequest;
import com.tasklean.api.auth.passwordreset.dto.ResetPasswordRequest;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.email.EmailTemplates;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Spy
    private EmailTemplates emailTemplates = new EmailTemplates("http://localhost:3000");

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User passwordUser() {
        return User.builder()
                .idUser(1L)
                .uid("usr-1")
                .email("bob@example.com")
                .passwordHash("$2a$10$old")
                .isActive(true)
                .isEmailVerified(false)
                .build();
    }

    private ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private ResetPasswordRequest reset(String token, String newPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        request.setNewPassword(newPassword);
        return request;
    }

    // --- requestReset ---

    @Test
    void requestReset_passwordAccount_invalidatesPriorAndSendsResetLink() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(passwordUser()));

        passwordResetService.requestReset(forgot("bob@example.com"));

        verify(tokenRepository).deleteByUserIdUser(1L);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).send(eq("bob@example.com"),
                argThat(content -> content.body().contains("/reset-password?token=")));
    }

    @Test
    void requestReset_unknownEmail_isSilentNoOp() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset(forgot("nobody@example.com"));

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void requestReset_deactivatedAccount_isSilentNoOp() {
        User deactivated = passwordUser();
        deactivated.setIsActive(false);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(deactivated));

        passwordResetService.requestReset(forgot("bob@example.com"));

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void requestReset_googleOnlyAccount_sendsInfoEmailWithoutToken() {
        User googleOnly = passwordUser();
        googleOnly.setPasswordHash(null);
        googleOnly.setGoogleSub("sub-123");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(googleOnly));

        passwordResetService.requestReset(forgot("bob@example.com"));

        verify(emailService).send("bob@example.com", emailTemplates.googleOnlyReset());
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).send(anyString(),
                argThat(content -> content.body().contains("/reset-password?token=")));
    }

    // --- resetPassword ---

    @Test
    void resetPassword_validToken_setsPasswordRevokesSessionsAndMarksUsed() {
        User user = passwordUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token("hashed")
                .used(false)
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                .build();
        when(tokenRepository.findByTokenAndUsedFalse(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("Nimbus7cloud!")).thenReturn("$2a$10$new");

        passwordResetService.resetPassword(reset("raw-token", "Nimbus7cloud!"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$new");
        assertThat(user.getIsEmailVerified()).isTrue();
        assertThat(token.getUsed()).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllUserTokens(user);
        verify(emailService).send("bob@example.com", emailTemplates.passwordChanged());
        verify(auditLogService).recordAccountEvent(eq(1L),
                eq(AuditAction.PASSWORD_RESET), anyString());
    }

    @Test
    void resetPassword_unknownOrUsedToken_throwsAndChangesNothing() {
        when(tokenRepository.findByTokenAndUsedFalse(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(reset("raw-token", "Nimbus7cloud!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired");

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllUserTokens(any());
    }

    @Test
    void resetPassword_expiredToken_throwsAndChangesNothing() {
        PasswordResetToken expired = PasswordResetToken.builder()
                .user(passwordUser())
                .token("hashed")
                .used(false)
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        when(tokenRepository.findByTokenAndUsedFalse(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.resetPassword(reset("raw-token", "Nimbus7cloud!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired");

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllUserTokens(any());
    }
}
