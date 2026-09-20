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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
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

    /**
     * Authenticates a user from a Google ID token, provisioning or linking an account as needed,
     * and issues access and refresh tokens.
     *
     * @param request the request carrying the Google ID token
     * @return the auth response with access and refresh tokens
     * @throws BadCredentialsException if the token is invalid, the Google email is unverified,
     *                                 or the matched account is deactivated
     */
    @Transactional
    public AuthResponse authenticate(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = verifyToken(request.getIdToken());

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
            auditLogService.recordEvent(AuditEntityType.USER, user.getIdUser(), AuditAction.LOGIN_FAILED,
                    "Failed Google login: account deactivated", null);
            throw new BadCredentialsException("Account is deactivated");
        }
        return issueTokens(user, AuditAction.LOGIN, "User logged in via Google");
    }

    private AuthResponse linkGoogleAccount(User user, GoogleIdToken.Payload payload) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            auditLogService.recordEvent(AuditEntityType.USER, user.getIdUser(), AuditAction.LOGIN_FAILED,
                    "Failed Google login: account deactivated", null);
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
        return issueTokens(user, AuditAction.LOGIN, "Linked Google sign-in to existing account");
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

    private AuthResponse issueTokens(User user, AuditAction action, String auditMessage) {
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        auditLogService.recordEvent(AuditEntityType.USER, user.getIdUser(), action, auditMessage, null);
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
