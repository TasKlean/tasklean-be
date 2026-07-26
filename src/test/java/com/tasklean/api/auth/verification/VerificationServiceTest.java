package com.tasklean.api.auth.verification;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.verification.dto.ResendVerificationRequest;
import com.tasklean.api.auth.verification.dto.VerifyEmailRequest;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private EmailVerificationRepository verificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private VerificationService verificationService;

    private User buildUnverifiedUser() {
        return User.builder()
                .idUser(1L)
                .uid("usr-test-123")
                .email("test@example.com")
                .name("Test")
                .isActive(true)
                .isEmailVerified(false)
                .build();
    }

    // --- createAndSendVerification ---

    @Test
    void createAndSendVerification_generatesCodeAndSendsEmail() {
        User user = buildUnverifiedUser();

        verificationService.createAndSendVerification(user);

        verify(verificationRepository).deleteByUserIdUser(1L);

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(verificationRepository).save(captor.capture());

        EmailVerification saved = captor.getValue();
        assertThat(saved.getCode()).hasSize(6);
        assertThat(saved.getCode()).matches("\\d{6}");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getUser()).isEqualTo(user);

        verify(emailService).sendVerificationEmail(eq("test@example.com"), eq(saved.getCode()));
    }

    // --- verifyEmail ---

    @Test
    void verifyEmail_validCode_marksVerifiedAndReturnsToken() {
        User user = buildUnverifiedUser();
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("test@example.com");
        request.setCode("123456");

        EmailVerification verification = EmailVerification.builder()
                .code("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .user(user)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(verificationRepository.findTopByUserIdUserAndCodeAndExpiresAtAfterOrderByDateCreatedDesc(
                eq(1L), eq("123456"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(verification));
        when(jwtService.generateToken(user)).thenReturn("verified-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token-xyz");

        AuthResponse response = verificationService.verifyEmail(request);

        assertThat(response.getToken()).isEqualTo("verified-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-xyz");
        assertThat(user.getIsEmailVerified()).isTrue();
        verify(userRepository).save(user);
        verify(verificationRepository).deleteByUserIdUser(1L);
    }

    @Test
    void verifyEmail_invalidCode_throwsGenericError() {
        User user = buildUnverifiedUser();
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("test@example.com");
        request.setCode("000000");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(verificationRepository.findTopByUserIdUserAndCodeAndExpiresAtAfterOrderByDateCreatedDesc(
                eq(1L), eq("000000"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.verifyEmail(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or code");
    }

    @Test
    void verifyEmail_alreadyVerified_throwsGenericError() {
        User user = buildUnverifiedUser();
        user.setIsEmailVerified(true);
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("test@example.com");
        request.setCode("123456");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> verificationService.verifyEmail(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or code");
    }

    @Test
    void verifyEmail_unknownEmail_throwsBadCredentials() {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setEmail("nobody@example.com");
        request.setCode("123456");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.verifyEmail(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or code");
    }

    // --- resendVerification ---

    @Test
    void resendVerification_unverifiedUser_sendsNewCode() {
        User user = buildUnverifiedUser();
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        verificationService.resendVerification(request);

        verify(verificationRepository).deleteByUserIdUser(1L);
        verify(verificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), any());
    }

    @Test
    void resendVerification_alreadyVerified_doesNothingSilently() {
        User user = buildUnverifiedUser();
        user.setIsEmailVerified(true);
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        verificationService.resendVerification(request);

        verify(verificationRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_unknownEmail_doesNothingSilently() {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("nobody@example.com");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        verificationService.resendVerification(request);

        verify(verificationRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }
}
