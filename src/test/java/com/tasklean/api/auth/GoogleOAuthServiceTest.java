package com.tasklean.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
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

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    private static final String TOKEN = "google-id-token";

    private GoogleAuthRequest request() {
        return new GoogleAuthRequest(TOKEN);
    }

    private GoogleIdToken.Payload payload(String sub, String email, boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.set("given_name", "Jane");
        payload.set("family_name", "Doe");
        payload.set("picture", "https://pics/jane.png");
        return payload;
    }

    private void stubVerify(GoogleIdToken.Payload payload) throws Exception {
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify(TOKEN)).thenReturn(token);
    }

    private User buildUser(boolean active) {
        return User.builder()
                .idUser(7L)
                .uid("usr-google-7")
                .email("jane@example.com")
                .name("Jane")
                .lastName("Doe")
                .googleSub("sub-123")
                .isActive(active)
                .isEmailVerified(true)
                .build();
    }

    // --- New user provisioning ---

    @Test
    void authenticate_newGoogleUser_createsVerifiedAccountAndIssuesTokens() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setIdUser(50L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = googleOAuthService.authenticate(request());

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getGoogleSub()).isEqualTo("sub-123");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getIsEmailVerified()).isTrue();
        assertThat(saved.getUid()).isNotBlank();
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(50L),
                eq(AuditAction.REGISTER), anyString(), isNull());
    }

    // --- Returning Google user ---

    @Test
    void authenticate_returningGoogleUser_logsInWithoutCreating() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.of(buildUser(true)));
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = googleOAuthService.authenticate(request());

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getUid()).isEqualTo("usr-google-7");
        verify(userRepository, never()).save(any());
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(7L),
                eq(AuditAction.LOGIN), anyString(), isNull());
    }

    // --- Linking a password account ---

    @Test
    void authenticate_existingEmail_linksGoogleAndVerifies() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        User passwordUser = User.builder()
                .idUser(9L)
                .uid("usr-pw-9")
                .email("jane@example.com")
                .passwordHash("$2a$10$hash")
                .name("Jane")
                .lastName("Doe")
                .isActive(true)
                .isEmailVerified(false)
                .build();
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(passwordUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = googleOAuthService.authenticate(request());

        assertThat(response.getToken()).isEqualTo("access-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User linked = captor.getValue();
        assertThat(linked.getGoogleSub()).isEqualTo("sub-123");
        assertThat(linked.getIsEmailVerified()).isTrue();
        assertThat(linked.getPhotoUrl()).isEqualTo("https://pics/jane.png");
        assertThat(linked.getPasswordHash()).isEqualTo("$2a$10$hash");
        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(9L),
                eq(AuditAction.LOGIN), anyString(), isNull());
    }

    // --- Rejections ---

    @Test
    void authenticate_invalidToken_throwsBadCredentials() throws Exception {
        when(googleIdTokenVerifier.verify(TOKEN)).thenReturn(null);

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid Google token");

        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_verifierThrows_throwsBadCredentials() throws Exception {
        when(googleIdTokenVerifier.verify(TOKEN)).thenThrow(new IOException("JWKS unreachable"));

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Could not verify Google token");
    }

    @Test
    void authenticate_unverifiedGoogleEmail_throwsBadCredentials() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", false));

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not verified");

        verify(userRepository, never()).findByGoogleSub(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_deactivatedAccount_throwsBadCredentialsAndAuditsFailure() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.of(buildUser(false)));

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("deactivated");

        verify(auditLogService).recordEvent(eq(AuditEntityType.USER), eq(7L),
                eq(AuditAction.LOGIN_FAILED), anyString(), isNull());
        verify(jwtService, never()).generateToken(any());
    }
}
