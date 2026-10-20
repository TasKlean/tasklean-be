package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.twofactor.TwoFactorChallengeService;
import com.tasklean.api.auth.verification.VerificationService;
import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.exception.AuthFailureException;
import com.tasklean.api.common.exception.EmailNotVerifiedException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles user registration and login. Creates new accounts with hashed passwords and issues JWT
 * tokens on successful authentication - unless the account has two-factor enabled, in which case
 * login returns a challenge and the tokens come from the verification step instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final TwoFactorChallengeService twoFactorChallengeService;

    /**
     * Creates a new user account, hashes the password, and sends a verification code.
     * No JWT is issued until the email is verified. The response is identical whether or not
     * the email already exists (anti-enumeration): an existing owner is notified by email
     * instead of the caller being told the address is taken.
     *
     * @param request the registration details (email, password, name)
     * @return a neutral pending-verification response
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User existing = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (existing != null) {
            // Don't reveal existence to the caller; tell the real owner by email.
            emailService.sendRegistrationAttemptEmail(request.getEmail());
            log.info("Registration attempt for existing email: uid={}", existing.getUid());
            return AuthResponse.registrationPending(request.getEmail());
        }

        User user = User.builder()
                .uid(UUID.randomUUID().toString())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .middleName(request.getMiddleName())
                .lastName(request.getLastName())
                .build();

        user = userRepository.save(user);
        // Log the uid, never the email (PII).
        log.info("User registered: uid={}", user.getUid());
        auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.REGISTER, "Account created");
        verificationService.createAndSendVerification(user);

        return AuthResponse.registrationPending(user.getEmail());
    }

    /**
     * Validates credentials against the stored hash and returns a JWT on success. An account
     * with both a password and a linked Google identity can sign in with either.
     *
     * @param request the login credentials (email, password)
     * @return the auth response with access and refresh tokens, or a two-factor challenge (no
     *         tokens) when the account has a second factor enabled
     * @throws EmailNotVerifiedException if the password is correct but the email is unverified
     * @throws BadCredentialsException   if the email is unknown, the password is wrong, the account
     *                                   is OAuth-only, or (after a correct password) it is deactivated
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check the password before revealing any account state, so a wrong guess is
        // indistinguishable from an unknown email (no account enumeration). A null hash is
        // an OAuth-only account, which matches no password and yields the same generic error.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(user, user.getPasswordHash() == null ? "oauth-only account" : "invalid password");
            // No error code here: one would tell the caller which of the two failed.
            throw new BadCredentialsException("Invalid email or password");
        }

        // Past here the caller proved they own the account, so account-state reasons are safe to surface.
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            recordFailedLogin(user, "account deactivated");
            throw new AuthFailureException("Account is deactivated", ErrorCodes.ACCOUNT_DEACTIVATED);
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            recordFailedLogin(user, "email not verified");
            // Specific subclass so the response carries the EMAIL_NOT_VERIFIED code (still a 401).
            throw new EmailNotVerifiedException("Email not verified. Check your inbox for a verification code");
        }

        // Credentials are proven, but with two-factor on the session is withheld until the code
        // step; no LOGIN audit entry here either, since the user isn't signed in yet.
        Optional<AuthResponse> challenge = twoFactorChallengeService.challenge(user);
        if (challenge.isPresent()) {
            return challenge.get();
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        log.info("User logged in: uid={}", user.getUid());
        auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.LOGIN, "User logged in");
        return AuthResponse.from(user, token, refreshToken);
    }

    // login() is not transactional, so each failed-login entry commits in its own
    // transaction and survives the BadCredentialsException thrown immediately after.
    private void recordFailedLogin(User user, String reason) {
        auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.LOGIN_FAILED,
                "Failed login attempt: " + reason);
    }
}
