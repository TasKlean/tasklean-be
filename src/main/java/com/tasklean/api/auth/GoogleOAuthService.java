package com.tasklean.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.twofactor.TwoFactorChallengeService;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Handles "Sign in with Google": verifies the Google ID token supplied by the client, then finds,
 * links, or provisions the matching user and issues our own JWT + refresh token. The client runs
 * the Google Sign-In flow itself; the backend only validates the resulting ID token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final TwoFactorChallengeService twoFactorChallengeService;

    /**
     * Authenticates a user from a Google ID token, provisioning or linking an account as needed,
     * and issues access and refresh tokens.
     *
     * @param request the request carrying the Google ID token and the nonce secret
     * @return the auth response with access and refresh tokens, or a two-factor challenge (no
     *         tokens) when the matched account has a second factor enabled
     * @throws BadCredentialsException if the token is invalid, the nonce doesn't match, the Google
     *                                 email is unverified, or the matched account is deactivated
     */
    @Transactional
    public AuthResponse authenticate(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyToken(request.getIdToken());
        verifyNonce(payload, request.getNonce());

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Google account email is not verified");
        }

        String googleSub = payload.getSubject();
        String email = payload.getEmail();

        // 1. Returning Google user — matched by the stable Google subject id.
        User existingBySub = userRepository.findByGoogleSub(googleSub).orElse(null);
        if (existingBySub != null) {
            return loginExisting(existingBySub);
        }

        // 2. A password account with the same (Google-verified) email — link Google to it.
        User existingByEmail = userRepository.findByEmail(email).orElse(null);
        if (existingByEmail != null) {
            return linkGoogleAccount(existingByEmail, payload);
        }

        // 3. First-time Google sign-in — provision a new, already-verified account.
        return createGoogleUser(payload);
    }

    /**
     * Verifies a Google ID token for account-linking: checks the token signature, the OIDC nonce,
     * and that Google has verified the email, then returns the Google subject id to link. Unlike
     * {@link #authenticate}, it neither creates nor mutates any user — the caller links the subject
     * to an already-authenticated account.
     *
     * @param request the request carrying the Google ID token and the nonce secret
     * @return the verified Google identity (subject id and email) to link
     * @throws BadCredentialsException if the token is invalid, the nonce doesn't match, or the
     *                                 Google email is unverified
     */
    public GoogleIdentity verifyLinkToken(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyToken(request.getIdToken());
        verifyNonce(payload, request.getNonce());
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Google account email is not verified");
        }
        return new GoogleIdentity(payload.getSubject(), payload.getEmail());
    }

    // OIDC nonce (implicit flow): the token's nonce claim must equal SHA-256 of the per-login
    // secret the client kept in its session cookie, binding the token to this login (anti-replay).
    private void verifyNonce(GoogleIdToken.Payload payload, String nonce) {
        String expected = sha256Hex(nonce);
        if (!expected.equals(payload.getNonce())) {
            throw new BadCredentialsException("Invalid Google token");
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK, so this never happens at runtime.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private GoogleIdToken.Payload verifyToken(String idToken) {
        try {
            GoogleIdToken verified = googleIdTokenVerifier.verify(idToken);
            if (verified == null) {
                throw new BadCredentialsException("Invalid Google token");
            }
            return verified.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            // Network/JWKS failure or malformed token — treat as an authentication failure.
            throw new BadCredentialsException("Could not verify Google token");
        }
    }

    private AuthResponse loginExisting(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.LOGIN_FAILED,
                    "Failed Google login: account deactivated");
            throw new BadCredentialsException("Account is deactivated");
        }
        return challengeOrTokens(user, AuditAction.LOGIN, "User logged in via Google");
    }

    private AuthResponse linkGoogleAccount(User user, GoogleIdToken.Payload payload) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.LOGIN_FAILED,
                    "Failed Google login: account deactivated");
            throw new BadCredentialsException("Account is deactivated");
        }
        user.setGoogleSub(payload.getSubject());
        // Google has verified this mailbox, so a previously unverified password account is now trusted.
        user.setIsEmailVerified(true);
        if (user.getPhotoUrl() == null) {
            user.setPhotoUrl((String) payload.get("picture"));
        }
        user = userRepository.save(user);
        log.info("Linked Google sign-in to existing account: uid={}", user.getUid());
        return challengeOrTokens(user, AuditAction.LOGIN, "Linked Google sign-in to existing account");
    }

    private AuthResponse createGoogleUser(GoogleIdToken.Payload payload) {
        User user = User.builder()
                .uid(UUID.randomUUID().toString())
                .email(payload.getEmail())
                .name(firstName(payload))
                .lastName(lastName(payload))
                .photoUrl((String) payload.get("picture"))
                .googleSub(payload.getSubject())
                // Google has already verified the email, so no verification step is required.
                .isEmailVerified(true)
                .build();
        user = userRepository.save(user);
        log.info("User registered via Google: uid={}", user.getUid());
        return issueTokens(user, AuditAction.REGISTER, "Account created via Google");
    }

    // A second factor applies to an existing account signing in; a freshly provisioned one can't
    // have one yet, so it goes straight to issueTokens.
    private AuthResponse challengeOrTokens(User user, AuditAction action, String auditMessage) {
        return twoFactorChallengeService.challenge(user)
                .orElseGet(() -> issueTokens(user, action, auditMessage));
    }

    private AuthResponse issueTokens(User user, AuditAction action, String auditMessage) {
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        auditLogService.recordAccountEvent(user.getIdUser(), action, auditMessage);
        return AuthResponse.from(user, token, refreshToken);
    }

    private String firstName(GoogleIdToken.Payload payload) {
        String given = (String) payload.get("given_name");
        if (given != null && !given.isBlank()) {
            return given;
        }
        String name = (String) payload.get("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        // Last resort: the local part of the email address.
        return payload.getEmail().split("@")[0];
    }

    private String lastName(GoogleIdToken.Payload payload) {
        String family = (String) payload.get("family_name");
        // Google may omit the family name; the column is non-null, so fall back to an empty string.
        return family != null && !family.isBlank() ? family : "";
    }
}
