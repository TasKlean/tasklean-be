package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.verification.VerificationService;
import com.tasklean.api.common.exception.DuplicateResourceException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles user registration and login. Creates new accounts with hashed passwords
 * and issues JWT tokens on successful authentication.
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

    /**
     * Creates a new user account, hashes the password, and sends a verification code.
     * No JWT is issued until the email is verified.
     *
     * @param request the registration details (email, password, name)
     * @return a pending-verification auth response
     * @throws DuplicateResourceException if the email is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
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
        auditLogService.record(AuditEntityType.USER, user.getIdUser(), AuditAction.REGISTER, "Account created", null);
        verificationService.createAndSendVerification(user);

        return AuthResponse.pendingVerification(user);
    }

    /**
     * Validates credentials against the stored hash and returns a JWT on success.
     *
     * @param request the login credentials (email, password)
     * @return the auth response with access and refresh tokens
     * @throws BadCredentialsException if the account is unknown, deactivated, unverified,
     *                                 uses a different sign-in option, or the password is wrong
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.getIsActive()) {
            recordFailedLogin(user, "account deactivated");
            throw new BadCredentialsException("Account is deactivated");
        }

        // Google OAuth users have no password — must use /api/auth/google instead
        if (user.getPasswordHash() == null || user.getGoogleSub() != null) {
            recordFailedLogin(user, "different sign-in option");
            throw new BadCredentialsException("This account uses a different sign-in option");
        }

        if (!user.getIsEmailVerified()) {
            recordFailedLogin(user, "email not verified");
            throw new BadCredentialsException("Email not verified. Check your inbox for a verification code");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(user, "invalid password");
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        log.info("User logged in: uid={}", user.getUid());
        auditLogService.record(AuditEntityType.USER, user.getIdUser(), AuditAction.LOGIN, "User logged in", null);
        return AuthResponse.from(user, token, refreshToken);
    }

    // login() is not transactional, so each failed-login entry commits in its own
    // transaction and survives the BadCredentialsException thrown immediately after.
    private void recordFailedLogin(User user, String reason) {
        auditLogService.record(AuditEntityType.USER, user.getIdUser(), AuditAction.LOGIN_FAILED,
                "Failed login attempt: " + reason, null);
    }
}
