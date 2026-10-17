package com.tasklean.api.auth.account;

import com.tasklean.api.auth.GoogleIdentity;
import com.tasklean.api.auth.GoogleOAuthService;
import com.tasklean.api.auth.account.dto.ChangePasswordRequest;
import com.tasklean.api.auth.account.dto.ConnectedAccountsResponse;
import com.tasklean.api.auth.account.dto.DeactivateAccountRequest;
import com.tasklean.api.auth.account.dto.SetPasswordRequest;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
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

/**
 * Self-service account-security operations for the authenticated caller: changing or setting a
 * password, managing connected sign-in methods (Google link/unlink), and deactivating the account.
 * A credential change revokes every existing session; for password change/set the current device
 * is immediately re-issued a fresh session so the caller stays signed in here while other devices
 * are forced to re-authenticate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleOAuthService googleOAuthService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    /**
     * Changes the caller's password after verifying the current one, then rotates sessions
     * (revoke all, keep this device) and emails a confirmation.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the current and new passwords
     * @return a fresh auth response (new access + refresh token) for the current device
     * @throws BusinessRuleException   if the account has no password yet (use set-password instead)
     * @throws BadCredentialsException if the current password is incorrect
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = loadActiveUser(userId);
        if (user.getPasswordHash() == null) {
            throw new BusinessRuleException("This account has no password yet. Set a password instead.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        AuthResponse response = rotateCurrentSession(user);

        emailService.sendPasswordChangedEmail(user.getEmail());
        auditLogService.recordAccountEvent(userId, AuditAction.PASSWORD_CHANGED, "Password changed");
        log.info("Password changed: uid={}", user.getUid());
        return response;
    }

    /**
     * Sets a password on a Google-only account (one that has none), then rotates sessions
     * (revoke all, keep this device) and emails a confirmation.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the new password
     * @return a fresh auth response (new access + refresh token) for the current device
     * @throws BusinessRuleException if the account already has a password (use change-password instead)
     */
    @Transactional
    public AuthResponse setPassword(Long userId, SetPasswordRequest request) {
        User user = loadActiveUser(userId);
        if (user.getPasswordHash() != null) {
            throw new BusinessRuleException("This account already has a password. Change it instead.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        AuthResponse response = rotateCurrentSession(user);

        emailService.sendPasswordSetEmail(user.getEmail());
        auditLogService.recordAccountEvent(userId, AuditAction.PASSWORD_SET, "Password set");
        log.info("Password set: uid={}", user.getUid());
        return response;
    }

    /**
     * Returns the caller's active sign-in methods (whether a password is set and Google is linked).
     *
     * @param userId the authenticated caller's internal id
     * @return the connections summary
     */
    @Transactional(readOnly = true)
    public ConnectedAccountsResponse getConnections(Long userId) {
        User user = loadActiveUser(userId);
        return connectionsOf(user);
    }

    /**
     * Links a Google account to the caller after verifying the Google ID token.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the Google ID token and nonce secret
     * @return the updated connections summary
     * @throws BusinessRuleException if a Google account is already linked here, the Google email
     *                               differs from the account email, or the verified Google account
     *                               is already linked to another user
     */
    @Transactional
    public ConnectedAccountsResponse linkGoogle(Long userId, GoogleAuthRequest request) {
        GoogleIdentity identity = googleOAuthService.verifyLinkToken(request);
        User user = loadActiveUser(userId);

        if (user.getGoogleSub() != null) {
            throw new BusinessRuleException("A Google account is already linked to this account.");
        }
        // Only a Google account whose email matches this account's may be linked, so a link can't
        // attach an unrelated Google identity to the account.
        if (!user.getEmail().equalsIgnoreCase(identity.email())) {
            throw new BusinessRuleException("The Google account's email must match your TasKlean account email.");
        }
        // The Google subject is unique per user, so it must not already belong to someone else.
        userRepository.findByGoogleSub(identity.subject())
                .filter(other -> !other.getIdUser().equals(userId))
                .ifPresent(other -> {
                    throw new BusinessRuleException("This Google account is already linked to another TasKlean account.");
                });

        user.setGoogleSub(identity.subject());
        userRepository.save(user);

        emailService.sendGoogleLinkedEmail(user.getEmail());
        auditLogService.recordAccountEvent(userId, AuditAction.GOOGLE_LINKED, "Google account linked");
        log.info("Google account linked: uid={}", user.getUid());
        return connectionsOf(user);
    }

    /**
     * Unlinks the caller's Google account. Refused if it would leave the account with no way to
     * sign in (no password set).
     *
     * @param userId the authenticated caller's internal id
     * @return the updated connections summary
     * @throws BusinessRuleException if no Google account is linked, or no password is set (lockout)
     */
    @Transactional
    public ConnectedAccountsResponse unlinkGoogle(Long userId) {
        User user = loadActiveUser(userId);

        if (user.getGoogleSub() == null) {
            throw new BusinessRuleException("No Google account is linked to this account.");
        }
        if (user.getPasswordHash() == null) {
            throw new BusinessRuleException("Set a password before unlinking Google, otherwise you won't be able to sign in.");
        }

        user.setGoogleSub(null);
        userRepository.save(user);

        emailService.sendGoogleUnlinkedEmail(user.getEmail());
        auditLogService.recordAccountEvent(userId, AuditAction.GOOGLE_UNLINKED, "Google account unlinked");
        log.info("Google account unlinked: uid={}", user.getUid());
        return connectionsOf(user);
    }

    /**
     * Deactivates (soft-deletes) the caller's account and signs out every session. A password
     * account must re-authenticate with its password; a Google-only account is confirmed by the
     * authenticated session alone.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the confirmation password (required only for a password account)
     * @throws BadCredentialsException if the account has a password and the supplied one is
     *                                 missing or incorrect
     */
    @Transactional
    public void deactivate(Long userId, DeactivateAccountRequest request) {
        User user = loadActiveUser(userId);

        if (user.getPasswordHash() != null) {
            String password = request.getPassword();
            if (password == null || password.isBlank()) {
                throw new BadCredentialsException("Password is required to deactivate your account");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BadCredentialsException("Current password is incorrect");
            }
        }

        user.setIsActive(false);
        userRepository.save(user);
        refreshTokenService.revokeAllUserTokens(user);

        emailService.sendAccountDeactivatedEmail(user.getEmail());
        auditLogService.recordAccountEvent(userId, AuditAction.ACCOUNT_DELETED, "Account deactivated");
        log.info("Account deactivated: uid={}", user.getUid());
    }

    // Revokes every session, then issues a fresh pair bound to the caller's current device so this
    // device stays signed in while all others must re-authenticate. The new token is inserted after
    // the bulk revoke, so it is not caught by it.
    private AuthResponse rotateCurrentSession(User user) {
        refreshTokenService.revokeAllUserTokens(user);
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return AuthResponse.from(user, accessToken, refreshToken);
    }

    private ConnectedAccountsResponse connectionsOf(User user) {
        return new ConnectedAccountsResponse(user.getPasswordHash() != null, user.getGoogleSub() != null);
    }

    private User loadActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("This account is not active.");
        }
        return user;
    }
}
