package com.tasklean.api.auth.passwordreset;

import com.tasklean.api.auth.passwordreset.dto.ForgotPasswordRequest;
import com.tasklean.api.auth.passwordreset.dto.ResetPasswordRequest;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Handles the forgot/reset-password flow. Reset tokens are high-entropy, stored SHA-256 hashed,
 * single-use, and short-lived; the raw token is only ever placed in the emailed link. Requests
 * never reveal whether an email exists (enumeration-safe), and a successful reset revokes every
 * existing session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_EXPIRY_MINUTES = 15;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Starts a password reset: emails a single-use reset link to a password account, or a "you use
     * Google" notice to a Google-only account. Unknown or deactivated accounts are a silent no-op.
     * The API response is always the same, so this never reveals whether the email is registered.
     *
     * @param request the email to send a reset link to
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return;
        }

        if (user.getPasswordHash() == null) {
            // Google-only account: there is no password to reset, so guide the owner by email.
            emailService.sendGoogleOnlyResetEmail(user.getEmail());
            log.info("Password reset requested for Google-only account: uid={}", user.getUid());
            return;
        }

        // Only the newest token stays valid: drop any outstanding ones first.
        tokenRepository.deleteByUserIdUser(user.getIdUser());

        String rawToken = SecureTokens.randomToken();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .token(SecureTokens.sha256(rawToken))
                .expiresAt(LocalDateTime.now(clock).plusMinutes(TOKEN_EXPIRY_MINUTES))
                .build());

        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        // Never log the raw token.
        log.info("Password reset requested: uid={}", user.getUid());
    }

    /**
     * Completes a password reset: validates the token, sets the new password, verifies the email,
     * marks the token used, and revokes all of the user's sessions.
     *
     * @param request the reset token and the new password
     * @throws BadCredentialsException if the token is unknown, already used, or expired
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByTokenAndUsedFalse(SecureTokens.sha256(request.getToken()))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new BadCredentialsException("Invalid or expired reset token");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // Receiving the emailed token proves control of the mailbox, so the email is now verified.
        user.setIsEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        // Force re-authentication everywhere — kills any session an attacker may hold.
        refreshTokenService.revokeAllUserTokens(user);

        log.info("Password reset completed: uid={}", user.getUid());
        auditLogService.recordEvent(AuditEntityType.USER, user.getIdUser(), AuditAction.PASSWORD_RESET, "Password reset", null);
    }

    /** Scheduled cleanup — deletes expired or already-used reset tokens every 6 hours. */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    @Transactional
    public void purgeExpiredAndUsedTokens() {
        tokenRepository.deleteByExpiresAtBeforeOrUsedTrue(LocalDateTime.now(clock));
    }
}
