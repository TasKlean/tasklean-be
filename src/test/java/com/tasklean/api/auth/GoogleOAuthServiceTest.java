package com.tasklean.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.twofactor.TwoFactorChallengeService;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.email.EmailTemplates;
import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private TwoFactorChallengeService twoFactorChallengeService;

    @Mock
    private EmailService emailService;

    @Spy
    private EmailTemplates emailTemplates = new EmailTemplates("http://localhost:3000");

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    private static final String TOKEN = "google-id-token";
    // Raw per-login secret the client sends; the token's nonce claim is its SHA-256.
    private static final String NONCE = "per-login-nonce-secret";

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private GoogleAuthRequest request() {
        return new GoogleAuthRequest(TOKEN, NONCE);
    }

    private GoogleIdToken.Payload payload(String sub, String email, boolean emailVerified) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.setNonce(sha256Hex(NONCE));
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

    /** A password account that never entered its verification code - what Google linking finds. */
    private User buildUnverifiedPasswordUser() {
        return User.builder()
                .idUser(9L)
                .uid("usr-pw-9")
                .email("jane@example.com")
                .passwordHash("$2a$10$hash")
                .name("Jane")
                .lastName("Doe")
                .isActive(true)
                .isEmailVerified(false)
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
        verify(auditLogService).recordAccountEvent(eq(50L),
                eq(AuditAction.REGISTER), anyString());
    }

    @Test
    void authenticate_newGoogleUser_sendsWelcome() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        googleOAuthService.authenticate(request());

        // Registering through Google skips verifyEmail, so without this the only email a new
        // Google account ever gets is the new-sign-in alert.
        verify(emailService).send("jane@example.com", emailTemplates.welcome());
    }

    // --- Provider values that outgrow their columns ---

    @Test
    void authenticate_oversizedGoogleProfile_clipsEachValueToItsColumn() throws Exception {
        GoogleIdToken.Payload payload = payload("sub-123", "jane@example.com", true);
        payload.set("given_name", "a".repeat(FieldLimits.NAME + 50));
        payload.set("family_name", "b".repeat(FieldLimits.NAME + 50));
        payload.set("picture", "https://pics/" + "c".repeat(FieldLimits.PHOTO_URL));
        stubVerify(payload);
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        googleOAuthService.authenticate(request());

        // Clipped rather than refused: the column would reject the insert, and failing a sign-in
        // over the length of someone's own profile name is worse than storing a trimmed one.
        User saved = savedUser();
        assertThat(saved.getName()).isEqualTo("a".repeat(FieldLimits.NAME));
        assertThat(saved.getLastName()).isEqualTo("b".repeat(FieldLimits.NAME));
        assertThat(saved.getPhotoUrl()).hasSize(FieldLimits.PHOTO_URL);
    }

    @Test
    void authenticate_blankGivenName_fallsBackToTheFullNameClipped() throws Exception {
        GoogleIdToken.Payload payload = payload("sub-123", "jane@example.com", true);
        payload.set("given_name", " ");
        payload.set("name", "d".repeat(FieldLimits.NAME + 50));
        stubVerify(payload);
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        googleOAuthService.authenticate(request());

        assertThat(savedUser().getName()).isEqualTo("d".repeat(FieldLimits.NAME));
    }

    @Test
    void authenticate_noNameAtAll_fallsBackToTheEmailLocalPartClipped() throws Exception {
        String localPart = "e".repeat(FieldLimits.NAME + 50);
        GoogleIdToken.Payload payload = payload("sub-123", localPart + "@example.com", true);
        payload.set("given_name", null);
        payload.set("family_name", null);
        stubVerify(payload);
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(localPart + "@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        googleOAuthService.authenticate(request());

        User saved = savedUser();
        assertThat(saved.getName()).isEqualTo("e".repeat(FieldLimits.NAME));
        // The column is non-null, so a missing family name becomes empty rather than null.
        assertThat(saved.getLastName()).isEmpty();
    }

    @Test
    void authenticate_blankFamilyNameAndNoPicture_storesEmptySurnameAndNoPhoto() throws Exception {
        GoogleIdToken.Payload payload = payload("sub-123", "jane@example.com", true);
        payload.set("family_name", " ");
        payload.set("picture", null);
        stubVerify(payload);
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        googleOAuthService.authenticate(request());

        User saved = savedUser();
        // The surname column is non-null, so a blank one becomes empty; a missing picture stays
        // missing rather than being invented.
        assertThat(saved.getLastName()).isEmpty();
        assertThat(saved.getPhotoUrl()).isNull();
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
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
        verify(auditLogService).recordAccountEvent(eq(7L),
                eq(AuditAction.LOGIN), anyString());
    }

    @Test
    void authenticate_returningUserWithTwoFactor_returnsChallengeAndWithholdsTokens() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.of(buildUser(true)));
        when(twoFactorChallengeService.challenge(any(User.class)))
                .thenReturn(Optional.of(AuthResponse.twoFactorRequired("challenge-token")));

        AuthResponse response = googleOAuthService.authenticate(request());

        assertThat(response.isTwoFactorRequired()).isTrue();
        assertThat(response.getToken()).isNull();
        verify(refreshTokenService, never()).createRefreshToken(any());
        verify(auditLogService, never()).recordAccountEvent(any(), eq(AuditAction.LOGIN), anyString());
    }

    // --- Linking a password account ---

    @Test
    void authenticate_existingEmail_linksGoogleAndVerifies() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(buildUnverifiedPasswordUser()));
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
        verify(auditLogService).recordAccountEvent(eq(9L),
                eq(AuditAction.LOGIN), anyString());
    }

    @Test
    void authenticate_existingEmail_linksWithoutWelcoming() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("jane@example.com"))
                .thenReturn(Optional.of(buildUnverifiedPasswordUser()));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        googleOAuthService.authenticate(request());

        // The welcome belongs to registration, not to the address becoming verified: linking adds
        // a sign-in method to an account that already existed.
        verify(emailService, never()).send(any(), any());
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
    void authenticate_nonceMismatch_throwsBadCredentials() throws Exception {
        GoogleIdToken.Payload payload = payload("sub-123", "jane@example.com", true);
        payload.setNonce("not-the-expected-hash");
        stubVerify(payload);

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid Google token");

        verify(userRepository, never()).findByGoogleSub(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_missingNonceInToken_throwsBadCredentials() throws Exception {
        GoogleIdToken.Payload payload = payload("sub-123", "jane@example.com", true);
        payload.setNonce(null);
        stubVerify(payload);

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid Google token");

        verify(userRepository, never()).findByGoogleSub(any());
    }

    // --- verifyLinkToken (account linking) ---

    @Test
    void verifyLinkToken_validToken_returnsSubjectWithoutTouchingUsers() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));

        GoogleIdentity identity = googleOAuthService.verifyLinkToken(request());

        assertThat(identity.subject()).isEqualTo("sub-123");
        assertThat(identity.email()).isEqualTo("jane@example.com");
        verify(userRepository, never()).findByGoogleSub(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyLinkToken_unverifiedEmail_throwsBadCredentials() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", false));

        assertThatThrownBy(() -> googleOAuthService.verifyLinkToken(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void verifyLinkToken_nonceMismatch_throwsBadCredentials() throws Exception {
        GoogleIdToken.Payload payload = payload("sub-123", "jane@example.com", true);
        payload.setNonce("not-the-expected-hash");
        stubVerify(payload);

        assertThatThrownBy(() -> googleOAuthService.verifyLinkToken(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid Google token");
    }

    @Test
    void authenticate_deactivatedAccount_throwsBadCredentialsAndAuditsFailure() throws Exception {
        stubVerify(payload("sub-123", "jane@example.com", true));
        when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.of(buildUser(false)));

        assertThatThrownBy(() -> googleOAuthService.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("deactivated");

        verify(auditLogService).recordAccountEvent(eq(7L),
                eq(AuditAction.LOGIN_FAILED), anyString());
        verify(jwtService, never()).generateToken(any());
    }
}
