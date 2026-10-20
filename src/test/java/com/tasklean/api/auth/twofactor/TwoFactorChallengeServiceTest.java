package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.JwtService;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.twofactor.dto.TwoFactorVerifyRequest;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorChallengeServiceTest {

    @Mock
    private TwoFactorChallengeRepository challengeRepository;
    @Mock
    private UserTwoFactorRepository userTwoFactorRepository;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private EmailService emailService;
    @Spy
    private Clock clock = Clock.systemUTC();
    @InjectMocks
    private TwoFactorChallengeService twoFactorChallengeService;

    private static final Long USER_ID = 1L;
    private static final String RAW_TOKEN = "challenge-token-raw";
    private static final String CODE = "123456";

    private User user() {
        return User.builder().idUser(USER_ID).uid("usr-1").email("bob@example.com")
                .name("Bob").isActive(true).isEmailVerified(true).build();
    }

    private UserTwoFactor factor(User owner, boolean enabled) {
        return UserTwoFactor.builder().idUserTwoFactor(5L).user(owner)
                .secretEncrypted("enc").isEnabled(enabled).build();
    }

    private TwoFactorChallenge challenge(User owner, LocalDateTime expiresAt, int attempts) {
        return TwoFactorChallenge.builder().idTwoFactorChallenge(9L).user(owner)
                .tokenHash(SecureTokens.sha256(RAW_TOKEN)).expiresAt(expiresAt).attempts(attempts).build();
    }

    private TwoFactorVerifyRequest verifyRequest(String code) {
        return TwoFactorVerifyRequest.builder().challengeToken(RAW_TOKEN).code(code).build();
    }

    private void stubLiveChallenge(User owner, int attempts) {
        when(challengeRepository.findByTokenHashAndConsumedAtIsNull(SecureTokens.sha256(RAW_TOKEN)))
                .thenReturn(Optional.of(challenge(owner, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4), attempts)));
    }

    // --- challengeIfEnabled ---

    @Test
    void challengeIfEnabled_noFactor_returnsEmpty() {
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());

        assertThat(twoFactorChallengeService.challenge(user())).isEmpty();
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void challengeIfEnabled_pendingSetupOnly_returnsEmpty() {
        User user = user();
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, false)));

        // An unconfirmed factor must not gate sign-in, or an abandoned setup would lock the user out.
        assertThat(twoFactorChallengeService.challenge(user)).isEmpty();
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void challengeIfEnabled_enabledFactor_storesHashedTokenWithExpiry() {
        User user = user();
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));

        Optional<AuthResponse> result = twoFactorChallengeService.challenge(user);

        assertThat(result).isPresent();
        AuthResponse response = result.orElseThrow();
        assertThat(response.isTwoFactorRequired()).isTrue();
        assertThat(response.getChallengeToken()).isNotBlank();
        assertThat(response.getToken()).isNull();
        // Nothing about the account is disclosed before the second factor is proven.
        assertThat(response.getUid()).isNull();
        assertThat(response.getEmail()).isNull();

        ArgumentCaptor<TwoFactorChallenge> captor = ArgumentCaptor.forClass(TwoFactorChallenge.class);
        verify(challengeRepository).save(captor.capture());
        TwoFactorChallenge stored = captor.getValue();
        assertThat(stored.getTokenHash()).isEqualTo(SecureTokens.sha256(response.getChallengeToken()));
        assertThat(stored.getTokenHash()).isNotEqualTo(response.getChallengeToken());
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(stored.getAttempts()).isZero();
    }

    @Test
    void challengeIfEnabled_enabledFactor_dropsThePreviousChallengeFirst() {
        User user = user();
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));

        twoFactorChallengeService.challenge(user);

        // Only the newest challenge may be live, so attempts can't be farmed across several.
        InOrder order = inOrder(challengeRepository);
        order.verify(challengeRepository).deleteByUserIdUser(USER_ID);
        order.verify(challengeRepository).save(any(TwoFactorChallenge.class));
    }

    // --- verify ---

    @Test
    void verify_validCode_consumesChallengeAndIssuesTokens() {
        User user = user();
        stubLiveChallenge(user, 0);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));
        when(twoFactorService.consumeCode(eq(user), any(UserTwoFactor.class), eq(CODE)))
                .thenReturn(TwoFactorCodeOutcome.TOTP);
        when(jwtService.generateToken(user)).thenReturn("access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh");

        AuthResponse response = twoFactorChallengeService.verify(verifyRequest(CODE));

        assertThat(response.getToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getUid()).isEqualTo("usr-1");
        ArgumentCaptor<TwoFactorChallenge> captor = ArgumentCaptor.forClass(TwoFactorChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertThat(captor.getValue().getConsumedAt()).isNotNull();
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.LOGIN), anyString());
        // An ordinary authenticator sign-in is not worth an email.
        verify(emailService, never()).sendRecoveryCodeUsedEmail(anyString());
    }

    @Test
    void verify_recoveryCode_alsoCompletesSignIn() {
        User user = user();
        stubLiveChallenge(user, 0);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));
        when(twoFactorService.consumeCode(eq(user), any(UserTwoFactor.class), eq("abcd-efgh")))
                .thenReturn(TwoFactorCodeOutcome.RECOVERY);
        when(jwtService.generateToken(user)).thenReturn("access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh");

        AuthResponse response = twoFactorChallengeService.verify(verifyRequest("abcd-efgh"));

        assertThat(response.getToken()).isEqualTo("access");
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.LOGIN), anyString());
        verify(emailService).sendRecoveryCodeUsedEmail("bob@example.com");
    }

    @Test
    void verify_wrongCode_countsTheAttemptAndIssuesNoTokens() {
        User user = user();
        stubLiveChallenge(user, 1);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));
        when(twoFactorService.consumeCode(eq(user), any(UserTwoFactor.class), eq("000000")))
                .thenReturn(TwoFactorCodeOutcome.INVALID);

        assertThatThrownBy(() -> twoFactorChallengeService.verify(verifyRequest("000000")))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<TwoFactorChallenge> captor = ArgumentCaptor.forClass(TwoFactorChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(2);
        assertThat(captor.getValue().getConsumedAt()).isNull();
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.LOGIN_FAILED), anyString());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void verify_attemptsExhausted_consumesChallengeWithoutCheckingTheCode() {
        User user = user();
        stubLiveChallenge(user, 5);

        assertThatThrownBy(() -> twoFactorChallengeService.verify(verifyRequest(CODE)))
                .isInstanceOf(BadCredentialsException.class);

        // Spent, so the caller must sign in again rather than keep hammering this row.
        ArgumentCaptor<TwoFactorChallenge> captor = ArgumentCaptor.forClass(TwoFactorChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertThat(captor.getValue().getConsumedAt()).isNotNull();
        verify(twoFactorService, never()).consumeCode(any(), any(), anyString());
    }

    @Test
    void verify_expiredChallenge_throwsAndIssuesNoTokens() {
        when(challengeRepository.findByTokenHashAndConsumedAtIsNull(SecureTokens.sha256(RAW_TOKEN)))
                .thenReturn(Optional.of(challenge(user(), LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), 0)));

        assertThatThrownBy(() -> twoFactorChallengeService.verify(verifyRequest(CODE)))
                .isInstanceOf(BadCredentialsException.class);

        verify(twoFactorService, never()).consumeCode(any(), any(), anyString());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void verify_unknownChallengeToken_throws() {
        when(challengeRepository.findByTokenHashAndConsumedAtIsNull(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorChallengeService.verify(verifyRequest(CODE)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verify_factorTurnedOffMeanwhile_throws() {
        User user = user();
        stubLiveChallenge(user, 0);
        // Two-factor was disabled from another device while this challenge was pending.
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorChallengeService.verify(verifyRequest(CODE)))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void verify_accountDeactivatedMeanwhile_throwsAndConsumesChallenge() {
        User deactivated = user();
        deactivated.setIsActive(false);
        stubLiveChallenge(deactivated, 0);

        assertThatThrownBy(() -> twoFactorChallengeService.verify(verifyRequest(CODE)))
                .isInstanceOf(BadCredentialsException.class);

        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.LOGIN_FAILED), anyString());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void purgeFinishedChallenges_deletesExpiredAndConsumedRows() {
        twoFactorChallengeService.purgeFinishedChallenges();

        verify(challengeRepository).deleteExpiredAndConsumed(any(LocalDateTime.class));
    }
}
