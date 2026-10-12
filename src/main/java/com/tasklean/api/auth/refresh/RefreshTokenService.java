package com.tasklean.api.auth.refresh;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.config.JwtConfig;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
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
 * Manages refresh tokens — issuing, rotating on refresh, revoking on logout, and
 * scheduled cleanup. Tokens are persisted SHA-256 hashed; the raw value is returned
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
    private final Clock clock;

    /**
     * Issues a new refresh token for the user.
     *
     * @param user the user to issue the token for
     * @return the raw (unhashed) token value to return to the caller
     */
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = SecureTokens.randomToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
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

        // Rotate: revoke old, issue new
        existing.setIsRevoked(true);
        refreshTokenRepository.save(existing);

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);

        // High-frequency (every ~15 min per active user) → DEBUG, not INFO.
        log.debug("Refresh token rotated: uid={}", user.getUid());
        return AuthResponse.from(user, newAccessToken, newRefreshToken);
    }

    /**
     * Revokes all of the user's refresh tokens, identified via the presented token (logout).
     *
     * @param request the refresh token identifying the user
     * @throws BadCredentialsException if the token is unknown or already revoked
     */
    @Transactional
    public void logout(RefreshRequest request) {
        String hashedToken = SecureTokens.sha256(request.getRefreshToken());

        RefreshToken token = refreshTokenRepository.findByTokenAndIsRevokedFalse(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        refreshTokenRepository.revokeAllByUserId(token.getUser().getIdUser());
        log.info("User logged out: uid={}", token.getUser().getUid());
        auditLogService.recordAccountEvent(token.getUser().getIdUser(), AuditAction.LOGOUT, "User logged out");
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
