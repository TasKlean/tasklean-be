package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.twofactor.TwoFactorChallengeService;
import com.tasklean.api.auth.verification.VerificationService;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.exception.EmailNotVerifiedException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private VerificationService verificationService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private EmailService emailService;

    @Mock
    private TwoFactorChallengeService twoFactorChallengeService;

    @InjectMocks
    private AuthService authService;

    private void verifyLoginFailedRecordedFor(long userId) {
        verify(auditLogService).recordAccountEvent(eq(userId),
                eq(AuditAction.LOGIN_FAILED), anyString());
    }

    private User buildExistingUser() {
        return User.builder()
                .idUser(1L)
                .uid("usr-existing-123")
                .email("bob@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .name("Bob")
                .lastName("Smith")
                .isActive(true)
                .isEmailVerified(true)
                .build();
    }

    // --- Register ---

    @Test
    void register_newEmail_createsUserAndSendsVerification() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setName("New");
        request.setLastName("User");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setIdUser(99L);
            return u;
        });

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isNull();
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getMessage()).contains("verification code");
        verify(verificationService).createAndSendVerification(any(User.class));
        verify(auditLogService).recordAccountEvent(eq(99L),
                eq(AuditAction.REGISTER), anyString());
    }

    @Test
    void register_hashesPasswordBeforeSaving() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("mypassword");
        request.setName("Test");
        request.setLastName("User");

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("mypassword")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void register_generatesUniqueUid() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setName("Test");
        request.setLastName("User");

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUid()).isNotBlank();
    }

    @Test
    void register_existingEmail_returnsNeutralResponseAndEmailsOwner() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password123");
        request.setName("Test");
        request.setLastName("User");

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(buildExistingUser()));

        AuthResponse response = authService.register(request);

        // Response echoes only the submitted email and the same neutral message as a new signup,
        // so the caller can't tell the address is already taken.
        assertThat(response.getToken()).isNull();
        assertThat(response.getUid()).isNull();
        assertThat(response.getName()).isNull();
        assertThat(response.getEmail()).isEqualTo("existing@example.com");
        assertThat(response.getMessage()).contains("verification code");
        // The real owner is notified by email; nothing is created and no code is sent.
        verify(emailService).sendRegistrationAttemptEmail("existing@example.com");
        verify(userRepository, never()).save(any());
        verify(verificationService, never()).createAndSendVerification(any());
    }

    // --- Login ---

    @Test
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("correctPassword");

        User user = buildExistingUser();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("login-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token-abc");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("login-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-abc");
        assertThat(response.getUid()).isEqualTo("usr-existing-123");
        assertThat(response.getEmail()).isEqualTo("bob@example.com");
        verify(auditLogService).recordAccountEvent(eq(1L),
                eq(AuditAction.LOGIN), anyString());
    }

    @Test
    void login_twoFactorEnabled_returnsChallengeAndWithholdsTokens() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("correctPassword");

        User user = buildExistingUser();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);
        when(twoFactorChallengeService.challenge(user))
                .thenReturn(Optional.of(AuthResponse.twoFactorRequired("challenge-token")));

        AuthResponse response = authService.login(request);

        assertThat(response.isTwoFactorRequired()).isTrue();
        assertThat(response.getChallengeToken()).isEqualTo("challenge-token");
        assertThat(response.getToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        // No session and no LOGIN entry yet - the user isn't signed in until the code step.
        verify(refreshTokenService, never()).createRefreshToken(any());
        verify(auditLogService, never()).recordAccountEvent(any(), eq(AuditAction.LOGIN), anyString());
    }

    @Test
    void login_nonExistentEmail_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("password");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");

        // Unknown email cannot be audited: there is no user id to attribute the entry to.
        verify(auditLogService, never()).recordAccountEvent(any(), any(), anyString());
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("wrongPassword");

        User user = buildExistingUser();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "$2a$10$hashedpassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");

        verifyLoginFailedRecordedFor(1L);
    }

    @Test
    void login_deactivatedAccount_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("password");

        User deactivated = buildExistingUser();
        deactivated.setIsActive(false);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(deactivated));
        // Deactivation is only revealed after a correct password (reordered checks).
        when(passwordEncoder.matches("password", "$2a$10$hashedpassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("deactivated");

        verifyLoginFailedRecordedFor(1L);
    }

    @Test
    void login_oauthOnlyAccount_throwsGenericBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("password");

        User oauthUser = buildExistingUser();
        oauthUser.setPasswordHash(null);
        oauthUser.setGoogleSub("google-sub-123");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(oauthUser));

        // No password set, so the reply is the same generic error as a wrong password — no hint
        // that this address exists or uses Google.
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");

        verifyLoginFailedRecordedFor(1L);
    }

    @Test
    void login_linkedAccountWithPassword_returnsToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("correctPassword");

        // Password account that later linked Google — Option B: either method signs in.
        User linked = buildExistingUser();
        linked.setGoogleSub("google-sub-123");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(linked));
        when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);
        when(jwtService.generateToken(linked)).thenReturn("login-token");
        when(refreshTokenService.createRefreshToken(linked)).thenReturn("refresh-token-abc");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("login-token");
        verify(auditLogService).recordAccountEvent(eq(1L),
                eq(AuditAction.LOGIN), anyString());
    }

    @Test
    void login_wrongPasswordOnUnverifiedAccount_throwsGenericNotEmailNotVerified() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("wrongPassword");

        User unverified = buildExistingUser();
        unverified.setIsEmailVerified(false);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(unverified));
        when(passwordEncoder.matches("wrongPassword", "$2a$10$hashedpassword")).thenReturn(false);

        // A wrong password must not leak the unverified state: generic error, not EMAIL_NOT_VERIFIED.
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .isNotInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("Invalid email or password");

        verifyLoginFailedRecordedFor(1L);
    }

    @Test
    void login_unverifiedEmail_throwsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("correctPassword");

        User unverified = buildExistingUser();
        unverified.setIsEmailVerified(false);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(unverified));
        when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not verified");

        verifyLoginFailedRecordedFor(1L);
    }

    @Test
    void login_unverifiedEmail_throwsEmailNotVerifiedException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bob@example.com");
        request.setPassword("correctPassword");

        User unverified = buildExistingUser();
        unverified.setIsEmailVerified(false);
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(unverified));
        when(passwordEncoder.matches("correctPassword", "$2a$10$hashedpassword")).thenReturn(true);

        // The specific subclass is what lets the response carry the EMAIL_NOT_VERIFIED code.
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyLoginFailedRecordedFor(1L);
    }
}
