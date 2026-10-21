package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.twofactor.dto.TwoFactorVerifyRequest;
import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.exception.AuthFailureException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The second step of signing in when two-factor is enabled: password or Google success issues a
 * short-lived challenge instead of tokens, and the session is only created once a code proves the
 * second factor. The challenge is an opaque random token stored hashed - deliberately not a JWT,
 * which the authentication filter would accept as a full session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorChallengeService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private static final int MAX_ATTEMPTS = 5;

    private final TwoFactorChallengeRepository challengeRepository;
    private final UserTwoFactorRepository userTwoFactorRepository;
    private final TwoFactorService twoFactorService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final Clock clock;

    /**
     * Issues a sign-in challenge when the user has an enabled second factor. Callers use the empty
     * result as "no second factor needed" and carry on issuing tokens.
     *
     * @param user the user who just passed the password or Google step
     * @return the challenge response to return to the client, or empty if two-factor is off
     */
    @Transactional
    public Optional<AuthResponse> challenge(User user) {
        boolean enabled = userTwoFactorRepository.findByUserIdUser(user.getIdUser())
                .filter(factor -> Boolean.TRUE.equals(factor.getIsEnabled()))
                .isPresent();
        if (!enabled) {
            return Optional.empty();
        }

        // Only the newest challenge stays live, so attempts can't be spread over a pile of them.
        challengeRepository.deleteByUserIdUser(user.getIdUser());

        String challengeToken = SecureTokens.randomToken();
        challengeRepository.save(TwoFactorChallenge.builder()
                .user(user)
                .tokenHash(SecureTokens.sha256(challengeToken))
                .expiresAt(LocalDateTime.now(clock).plus(CHALLENGE_TTL))
                .build());

        log.debug("Two-factor challenge issued: uid={}", user.getUid());
        return Optional.of(AuthResponse.twoFactorRequired(challengeToken));
    }

    /**
     * Completes a challenged sign-in: spends the submitted code and, on success, issues the access
     * and refresh tokens the password step withheld.
     *
     * @param request the challenge token and the authenticator or recovery code
     * @return the auth response with access and refresh tokens
     * @throws BadCredentialsException if the challenge is unknown, expired, already used or out of
     *                                 attempts, the account was deactivated meanwhile, or the code
     *                                 is wrong
     */
    public AuthResponse verify(TwoFactorVerifyRequest request) {
        TwoFactorChallenge challenge = challengeRepository
                .findByTokenHashAndConsumedAtIsNull(SecureTokens.sha256(request.getChallengeToken()))
                .orElseThrow(() -> new AuthFailureException(ErrorMessages.TWO_FACTOR_CHALLENGE_INVALID,
                        ErrorCodes.TWO_FACTOR_CHALLENGE_INVALID));
        User user = challenge.getUser();

        if (challenge.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new AuthFailureException(ErrorMessages.TWO_FACTOR_CHALLENGE_INVALID,
                    ErrorCodes.TWO_FACTOR_CHALLENGE_INVALID);
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            // Spend it, so a caller out of attempts can't keep the row alive by trying again.
            consume(challenge);
            log.warn("Two-factor challenge exhausted: uid={}", user.getUid());
            throw new AuthFailureException("Too many incorrect codes. Please sign in again.",
                    ErrorCodes.TWO_FACTOR_CHALLENGE_INVALID);
        }
        // The password step checked this, but the account may have been deactivated since.
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            consume(challenge);
            recordFailure(user, "account deactivated");
            throw new AuthFailureException(ErrorMessages.ACCOUNT_DEACTIVATED, ErrorCodes.ACCOUNT_DEACTIVATED);
        }

        UserTwoFactor factor = userTwoFactorRepository.findByUserIdUser(user.getIdUser())
                .filter(enabled -> Boolean.TRUE.equals(enabled.getIsEnabled()))
                .orElseThrow(() -> new AuthFailureException(ErrorMessages.TWO_FACTOR_CHALLENGE_INVALID,
                        ErrorCodes.TWO_FACTOR_CHALLENGE_INVALID));

        TwoFactorCodeOutcome outcome = twoFactorService.consumeCode(user, factor, request.getCode());
        if (outcome == TwoFactorCodeOutcome.INVALID) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            challengeRepository.save(challenge);
            recordFailure(user, "invalid two-factor code");
            log.warn("Two-factor verification failed: uid={}", user.getUid());
            throw new AuthFailureException(ErrorMessages.TWO_FACTOR_CODE_INVALID,
                    ErrorCodes.TWO_FACTOR_CODE_INVALID);
        }

        consume(challenge);
        // Signing in without the authenticator app is the weakest two-factor path, so the owner is
        // told even though the sign-in succeeded.
        if (outcome == TwoFactorCodeOutcome.RECOVERY) {
            emailService.sendRecoveryCodeUsedEmail(user.getEmail());
        }
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.LOGIN, "User logged in with two-factor");
        log.info("User logged in with two-factor: uid={}", user.getUid());
        return AuthResponse.from(user, token, refreshToken);
    }

    /** Scheduled cleanup — deletes expired and consumed challenges every hour. */
    @Scheduled(fixedRate = 60 * 60 * 1000) // every hour
    @Transactional
    public void purgeFinishedChallenges() {
        challengeRepository.deleteExpiredAndConsumed(LocalDateTime.now(clock));
    }

    private void consume(TwoFactorChallenge challenge) {
        challenge.setConsumedAt(LocalDateTime.now(clock));
        challengeRepository.save(challenge);
    }

    // verify() is not transactional, so the attempt counter and this entry commit on their own and
    // survive the BadCredentialsException thrown right after — as in AuthService.login().
    private void recordFailure(User user, String reason) {
        auditLogService.recordAccountEvent(user.getIdUser(), AuditAction.LOGIN_FAILED,
                "Failed two-factor verification: " + reason);
    }
}
