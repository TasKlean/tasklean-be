package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.SessionRotator;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.twofactor.dto.*;
import com.tasklean.api.auth.twofactor.provider.FactorEnrollment;
import com.tasklean.api.auth.twofactor.provider.TwoFactorProvider;
import com.tasklean.api.auth.twofactor.provider.TwoFactorVerification;
import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.SecretCipher;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.email.EmailTemplates;
import com.tasklean.api.common.exception.AuthFailureException;
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

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two-factor enrollment and management for the authenticated caller: starting setup, confirming it
 * with a code, turning it off, and issuing recovery codes. The factor itself is owned by the
 * {@link TwoFactorProvider} port; this service owns the state around it - what is stored, what
 * re-authentication each change needs, and which codes have been spent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 8;

    // No look-alike characters (no i, l, o, 0, 1), so a code copied off paper is hard to mistype.
    private static final String RECOVERY_CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTwoFactorRepository userTwoFactorRepository;
    private final TwoFactorRecoveryCodeRepository recoveryCodeRepository;
    private final UserRepository userRepository;
    private final TwoFactorProvider twoFactorProvider;
    private final SecretCipher secretCipher;
    private final PasswordEncoder passwordEncoder;
    private final SessionRotator sessionRotator;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Returns the caller's two-factor state for the settings screen.
     *
     * @param userId the authenticated caller's internal id
     * @return whether a factor is enabled or pending, when it was confirmed, and how many recovery
     *         codes are left
     */
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getStatus(Long userId) {
        UserTwoFactor factor = userTwoFactorRepository.findByUserIdUser(userId).orElse(null);
        boolean enabled = factor != null && Boolean.TRUE.equals(factor.getIsEnabled());
        boolean pendingSetup = factor != null && !Boolean.TRUE.equals(factor.getIsEnabled());
        return new TwoFactorStatusResponse(
                enabled,
                pendingSetup,
                enabled ? factor.getConfirmedAt() : null,
                enabled ? recoveryCodeRepository.countByUserIdUserAndUsedAtIsNull(userId) : 0L);
    }

    /**
     * Starts two-factor setup: enrolls a new factor and stores it unconfirmed, so an abandoned
     * setup changes nothing about how the account signs in.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the confirmation password (required only for a password account)
     * @return the secret and {@code otpauth://} URI for the authenticator app
     * @throws BusinessRuleException   if two-factor is already enabled
     * @throws BadCredentialsException if the account has a password and the supplied one is missing
     *                                 or incorrect
     */
    @Transactional
    public TwoFactorSetupResponse startSetup(Long userId, TwoFactorSetupRequest request) {
        User user = loadActiveUser(userId);
        requireReauthentication(user, request.getPassword());

        UserTwoFactor factor = userTwoFactorRepository.findByUserIdUser(userId).orElse(null);
        if (factor != null && Boolean.TRUE.equals(factor.getIsEnabled())) {
            throw new BusinessRuleException("Two-factor authentication is already on. Turn it off before setting it up again.");
        }

        FactorEnrollment enrollment = twoFactorProvider.enroll(user.getEmail());
        if (factor == null) {
            factor = UserTwoFactor.builder().user(user).build();
        }
        factor.setProvider(twoFactorProvider.type());
        // A local factor's credential is the secret, so it is encrypted at rest; a hosted provider
        // would return an opaque reference for factorRef instead.
        factor.setSecretEncrypted(secretCipher.encrypt(enrollment.credential()));
        // Re-running setup replaces an abandoned one, so no stale state survives it.
        factor.setIsEnabled(false);
        factor.setConfirmedAt(null);
        factor.setLastUsedStep(null);
        userTwoFactorRepository.save(factor);

        return new TwoFactorSetupResponse(enrollment.credential(), enrollment.otpauthUri());
    }

    /**
     * Confirms a pending setup with a code from the authenticator app, turning two-factor on. The
     * confirming code is recorded as used so it cannot be replayed at sign-in, every other session
     * is signed out, and the recovery codes are returned once.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the code currently shown by the authenticator app
     * @return the recovery codes plus a fresh session for the current device
     * @throws BusinessRuleException   if no setup is pending, or two-factor is already enabled
     * @throws BadCredentialsException if the code is not valid
     */
    @Transactional
    public TwoFactorEnableResponse enable(Long userId, TwoFactorEnableRequest request) {
        User user = loadActiveUser(userId);
        UserTwoFactor factor = userTwoFactorRepository.findByUserIdUser(userId)
                .orElseThrow(() -> new BusinessRuleException("Start two-factor setup before turning it on."));
        if (Boolean.TRUE.equals(factor.getIsEnabled())) {
            throw new BusinessRuleException("Two-factor authentication is already on.");
        }

        // Only the authenticator code counts here: there are no recovery codes to use yet.
        TwoFactorVerification verification = twoFactorProvider.verify(
                secretCipher.decrypt(factor.getSecretEncrypted()), request.getCode());
        if (!verification.valid()) {
            log.warn("Two-factor setup confirmation rejected: uid={}", user.getUid());
            throw new AuthFailureException("That code is not valid. Check the app and enter the current code.",
                    ErrorCodes.TWO_FACTOR_CODE_INVALID);
        }

        factor.setIsEnabled(true);
        factor.setConfirmedAt(LocalDateTime.now(clock));
        // Recorded now so the confirming code cannot be replayed at the next sign-in.
        factor.setLastUsedStep(verification.timeStep());
        userTwoFactorRepository.save(factor);

        List<String> recoveryCodes = replaceRecoveryCodes(user);
        AuthResponse session = sessionRotator.rotateCurrentSession(user);

        emailService.send(user.getEmail(), emailTemplates.twoFactorEnabled());
        auditLogService.recordAccountEvent(userId, AuditAction.TWO_FACTOR_ENABLED, "Two-factor authentication enabled");
        log.info("Two-factor enabled: uid={}", user.getUid());
        return new TwoFactorEnableResponse(recoveryCodes, session.getToken(), session.getRefreshToken());
    }

    /**
     * Turns two-factor off, removing the stored factor and every recovery code. Other sessions are
     * signed out and the current device gets a fresh session.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the confirmation password (required only for a password account) and a current
     *                authenticator or recovery code
     * @return a fresh auth response (new tokens) for the current device
     * @throws BusinessRuleException   if two-factor is not enabled
     * @throws BadCredentialsException if the password or the code is missing or incorrect
     */
    @Transactional
    public AuthResponse disable(Long userId, TwoFactorConfirmRequest request) {
        User user = loadActiveUser(userId);
        requireReauthentication(user, request.getPassword());
        UserTwoFactor factor = loadEnabledFactor(userId);
        requireValidCode(user, factor, request.getCode());

        // A credential table with no is_active column: the row is removed rather than soft-deleted,
        // so no disabled secret stays at rest. Refresh and verification tokens work the same way.
        recoveryCodeRepository.deleteByUserIdUser(userId);
        userTwoFactorRepository.delete(factor);
        AuthResponse session = sessionRotator.rotateCurrentSession(user);

        emailService.send(user.getEmail(), emailTemplates.twoFactorDisabled());
        auditLogService.recordAccountEvent(userId, AuditAction.TWO_FACTOR_DISABLED, "Two-factor authentication disabled");
        log.info("Two-factor disabled: uid={}", user.getUid());
        return session;
    }

    /**
     * Issues a fresh set of recovery codes, invalidating the previous set. Sessions are untouched -
     * the account's sign-in strength does not change.
     *
     * @param userId  the authenticated caller's internal id
     * @param request the confirmation password (required only for a password account) and a current
     *                authenticator or recovery code
     * @return the new recovery codes, returned only here
     * @throws BusinessRuleException   if two-factor is not enabled
     * @throws BadCredentialsException if the password or the code is missing or incorrect
     */
    @Transactional
    public RecoveryCodesResponse regenerateRecoveryCodes(Long userId, TwoFactorConfirmRequest request) {
        User user = loadActiveUser(userId);
        requireReauthentication(user, request.getPassword());
        UserTwoFactor factor = loadEnabledFactor(userId);
        requireValidCode(user, factor, request.getCode());

        List<String> recoveryCodes = replaceRecoveryCodes(user);

        emailService.send(user.getEmail(), emailTemplates.recoveryCodesRegenerated());
        auditLogService.recordAccountEvent(userId, AuditAction.RECOVERY_CODES_REGENERATED, "Recovery codes regenerated");
        log.info("Recovery codes regenerated: uid={}", user.getUid());
        return new RecoveryCodesResponse(recoveryCodes);
    }

    /**
     * Checks a submitted second-factor code against the user's factor and spends it. An
     * authenticator code counts once per time step (a repeat inside the skew window is a replay);
     * a recovery code counts once ever and is marked used. It writes, so it must be called from
     * inside the caller's transaction - it does not open one of its own.
     *
     * @param user   the account the factor belongs to
     * @param factor the user's enabled factor
     * @param code   the code the user submitted, authenticator or recovery
     * @return how the code resolved, {@link TwoFactorCodeOutcome#INVALID} when it did not match
     */
    public TwoFactorCodeOutcome consumeCode(User user, UserTwoFactor factor, String code) {
        if (code == null || code.isBlank()) {
            return TwoFactorCodeOutcome.INVALID;
        }

        TwoFactorVerification verification = twoFactorProvider.verify(
                secretCipher.decrypt(factor.getSecretEncrypted()), code);
        if (verification.valid()) {
            Long step = verification.timeStep();
            // The skew window accepts one code across several requests, so a step at or below the
            // last accepted one is a replay of a code that was already used.
            if (step != null && factor.getLastUsedStep() != null && step <= factor.getLastUsedStep()) {
                log.warn("Replayed two-factor code rejected: uid={}", user.getUid());
                return TwoFactorCodeOutcome.INVALID;
            }
            factor.setLastUsedStep(step);
            userTwoFactorRepository.save(factor);
            return TwoFactorCodeOutcome.TOTP;
        }

        return recoveryCodeRepository
                .findByUserIdUserAndCodeHashAndUsedAtIsNull(user.getIdUser(), hashRecoveryCode(code))
                .map(recoveryCode -> {
                    recoveryCode.setUsedAt(LocalDateTime.now(clock));
                    recoveryCodeRepository.save(recoveryCode);
                    auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.TWO_FACTOR_RECOVERY_USED,
                            "Two-factor recovery code used");
                    log.info("Two-factor recovery code used: uid={}", user.getUid());
                    return TwoFactorCodeOutcome.RECOVERY;
                })
                .orElse(TwoFactorCodeOutcome.INVALID);
    }

    private void requireValidCode(User user, UserTwoFactor factor, String code) {
        if (consumeCode(user, factor, code) == TwoFactorCodeOutcome.INVALID) {
            log.warn("Two-factor code rejected: uid={}", user.getUid());
            throw new AuthFailureException(ErrorMessages.TWO_FACTOR_CODE_INVALID,
                    ErrorCodes.TWO_FACTOR_CODE_INVALID);
        }
    }

    // Replaces the whole set at once: codes are stored hashed, so a partial refresh would leave the
    // user holding codes they can no longer tell apart from the live ones.
    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodeRepository.deleteByUserIdUser(user.getIdUser());
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            codes.add(code);
            recoveryCodeRepository.save(TwoFactorRecoveryCode.builder()
                    .user(user)
                    .codeHash(hashRecoveryCode(code))
                    .build());
        }
        return codes;
    }

    private String randomRecoveryCode() {
        StringBuilder code = new StringBuilder(RECOVERY_CODE_LENGTH + 1);
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            // Two groups read back more reliably; the separator is dropped before hashing.
            if (i == RECOVERY_CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(RECOVERY_CODE_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    // Hashing the separator-free lowercase form lets a code match in whatever shape it was typed.
    private String hashRecoveryCode(String code) {
        return SecureTokens.sha256(code.trim().toLowerCase(Locale.ROOT).replace("-", "").replace(" ", ""));
    }

    private UserTwoFactor loadEnabledFactor(Long userId) {
        return userTwoFactorRepository.findByUserIdUser(userId)
                .filter(factor -> Boolean.TRUE.equals(factor.getIsEnabled()))
                .orElseThrow(() -> new BusinessRuleException("Two-factor authentication is not on for this account."));
    }

    // A Google-only account has no password to re-check, so the authenticated session is the only
    // confirmation available - the same rule account deactivation uses.
    private void requireReauthentication(User user, String password) {
        if (user.getPasswordHash() == null) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new AuthFailureException("Password is required to change two-factor settings", ErrorCodes.PASSWORD_INVALID);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthFailureException(ErrorMessages.PASSWORD_INCORRECT, ErrorCodes.PASSWORD_INVALID);
        }
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
