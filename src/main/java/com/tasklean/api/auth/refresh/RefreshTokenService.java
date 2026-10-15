package com.tasklean.api.auth.refresh;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.device.Device;
import com.tasklean.api.domain.device.DeviceLoginOutcome;
import com.tasklean.api.domain.device.DeviceService;
import com.tasklean.api.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Manages refresh tokens and the sign-in sessions they represent — issuing a token bound to the
 * caller's device, rotating on refresh (keeping the same device), per-device and all-device
 * logout, and scheduled cleanup. Tokens are persisted SHA-256 hashed; the raw value is returned
 * only to the caller and is never stored or logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final AuditLogService auditLogService;
    private final DeviceService deviceService;
    private final EmailService emailService;
    private final Clock clock;

    /**
     * Issues a new refresh token for the user, binding it to the device resolved from the current
     * request. A first sign-in on an unseen device also sends a new-sign-in alert email.
     *
     * @param user the user to issue the token for
     * @return the raw (unhashed) token value to return to the caller
     */
    @Transactional
    public String createRefreshToken(User user) {
        DeviceLoginOutcome outcome = deviceService.resolveLoginDevice(user);
        String rawToken = persistToken(user, outcome.device());

        if (outcome.isNew() && outcome.device() != null) {
            Device device = outcome.device();
            emailService.sendNewSignInEmail(user.getEmail(), device.getDeviceName(), device.getIpAddress());
            auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.NEW_DEVICE_LOGIN,
                    "New device signed in: " + device.getDeviceName());
        }
        return rawToken;
    }

    // Persists a hashed refresh token for the user on the given device (null for a device-less
    // login or legacy rotation). Returns the raw value for the caller.
    private String persistToken(User user, Device device) {
        String rawToken = SecureTokens.randomToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .device(device)
                .token(SecureTokens.sha256(rawToken))
                .expiresAt(LocalDateTime.now(clock).plus(Duration.ofMillis(jwtConfig.getRefreshExpiration())))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * Rotates a valid refresh token: revokes the presented one and issues a fresh
     * access + refresh pair.
     *
     * @param request the current refresh token
     * @return the refreshed auth response
     * @throws BadCredentialsException if the token is unknown, revoked, or expired
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hashedToken = SecureTokens.sha256(request.getRefreshToken());

        RefreshToken existing = refreshTokenRepository.findByTokenAndIsRevokedFalse(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            existing.setIsRevoked(true);
            refreshTokenRepository.save(existing);
            throw new BadCredentialsException("Refresh token expired");
        }

        User user = existing.getUser();

        // Deny refresh if user is deactivated
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            refreshTokenRepository.revokeAllByUserId(user.getIdUser());
            throw new BadCredentialsException("Account is deactivated");
        }

        // Rotate: revoke old, issue a new token on the same device (session identity is preserved).
        existing.setIsRevoked(true);
        refreshTokenRepository.save(existing);

        Device device = existing.getDevice();
        if (device != null) {
            device.setLastUsed(LocalDateTime.now(clock));
        }

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = persistToken(user, device);

        // High-frequency (every ~15 min per active user) → DEBUG, not INFO.
        log.debug("Refresh token rotated: uid={}", user.getUid());
        return AuthResponse.from(user, newAccessToken, newRefreshToken);
    }

    /**
     * Logs out the current device: revokes every refresh token on the device the presented token
     * belongs to. A legacy token with no device revokes only itself.
     *
     * @param request the refresh token identifying the device/session
     * @throws BadCredentialsException if the token is unknown or already revoked
     */
    @Transactional
    public void logout(RefreshRequest request) {
        String hashedToken = SecureTokens.sha256(request.getRefreshToken());

        RefreshToken token = refreshTokenRepository.findByTokenAndIsRevokedFalse(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        Device device = token.getDevice();
        if (device != null) {
            refreshTokenRepository.revokeAllByDeviceId(device.getIdDevice());
        } else {
            token.setIsRevoked(true);
            refreshTokenRepository.save(token);
        }
        log.info("User logged out: uid={}", token.getUser().getUid());
        auditLogService.recordAccountEvent(token.getUser().getIdUser(), AuditAction.LOGOUT, "User logged out");
    }

    /**
     * Logs out everywhere: revokes every refresh token belonging to the user the presented token
     * identifies. Symmetric with {@link #logout(RefreshRequest)}, which revokes only one device.
     *
     * @param request the refresh token identifying the user
     * @throws BadCredentialsException if the token is unknown or already revoked
     */
    @Transactional
    public void logoutAll(RefreshRequest request) {
        String hashedToken = SecureTokens.sha256(request.getRefreshToken());

        RefreshToken token = refreshTokenRepository.findByTokenAndIsRevokedFalse(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        Long userId = token.getUser().getIdUser();

        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All sessions revoked: uid={}", token.getUser().getUid());
        auditLogService.recordAccountEvent(userId, AuditAction.ALL_SESSIONS_REVOKED, "Logged out of all sessions");
    }

    /**
     * Revokes every refresh token belonging to the given user.
     *
     * @param user the user whose tokens should be revoked
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllByUserId(user.getIdUser());
    }

    /** Scheduled cleanup — deletes expired and revoked tokens every 6 hours. */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // every 6 hours
    @Transactional
    public void purgeExpiredAndRevokedTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now(clock));
    }
}
