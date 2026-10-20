package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.SessionRotator;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.twofactor.dto.*;
import com.tasklean.api.auth.twofactor.provider.FactorEnrollment;
import com.tasklean.api.auth.twofactor.provider.TwoFactorProvider;
import com.tasklean.api.auth.twofactor.provider.TwoFactorProviderType;
import com.tasklean.api.auth.twofactor.provider.TwoFactorVerification;
import com.tasklean.api.common.SecretCipher;
import com.tasklean.api.common.SecureTokens;
import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.exception.AuthFailureException;
import com.tasklean.api.common.email.EmailService;
import com.tasklean.api.common.exception.BusinessRuleException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock
    private UserTwoFactorRepository userTwoFactorRepository;
    @Mock
    private TwoFactorRecoveryCodeRepository recoveryCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TwoFactorProvider twoFactorProvider;
    @Mock
    private SecretCipher secretCipher;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionRotator sessionRotator;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private Clock clock = Clock.systemUTC();
    @InjectMocks
    private TwoFactorService twoFactorService;

    private static final Long USER_ID = 1L;
    private static final String CODE = "123456";

    private User user(String passwordHash) {
        return User.builder().idUser(USER_ID).uid("usr-1").email("bob@example.com")
                .name("Bob").passwordHash(passwordHash).isActive(true).build();
    }

    private UserTwoFactor factor(User owner, boolean enabled) {
        return UserTwoFactor.builder().idUserTwoFactor(5L).user(owner)
                .provider(TwoFactorProviderType.LOCAL).secretEncrypted("enc")
                .digest(UserTwoFactor.DEFAULT_DIGEST).isEnabled(enabled).build();
    }

    private String hashOf(String recoveryCode) {
        return SecureTokens.sha256(recoveryCode.replace("-", ""));
    }

    // --- getStatus ---

    @Test
    void getStatus_noFactor_reportsNeitherEnabledNorPending() {
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());

        TwoFactorStatusResponse status = twoFactorService.getStatus(USER_ID);

        assertThat(status.enabled()).isFalse();
        assertThat(status.pendingSetup()).isFalse();
        assertThat(status.recoveryCodesRemaining()).isZero();
        verify(recoveryCodeRepository, never()).countByUserIdUserAndUsedAtIsNull(any());
    }

    @Test
    void getStatus_unconfirmedFactor_reportsPendingSetup() {
        when(userTwoFactorRepository.findByUserIdUser(USER_ID))
                .thenReturn(Optional.of(factor(user("$hash"), false)));

        TwoFactorStatusResponse status = twoFactorService.getStatus(USER_ID);

        assertThat(status.enabled()).isFalse();
        assertThat(status.pendingSetup()).isTrue();
        // An abandoned setup has no codes to report yet.
        assertThat(status.recoveryCodesRemaining()).isZero();
    }

    @Test
    void getStatus_enabledFactor_reportsConfirmedAtAndRemainingCodes() {
        UserTwoFactor enabled = factor(user("$hash"), true);
        enabled.setConfirmedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(enabled));
        when(recoveryCodeRepository.countByUserIdUserAndUsedAtIsNull(USER_ID)).thenReturn(7L);

        TwoFactorStatusResponse status = twoFactorService.getStatus(USER_ID);

        assertThat(status.enabled()).isTrue();
        assertThat(status.pendingSetup()).isFalse();
        assertThat(status.confirmedAt()).isEqualTo(enabled.getConfirmedAt());
        assertThat(status.recoveryCodesRemaining()).isEqualTo(7L);
    }

    // --- startSetup ---

    private void stubEnrollment() {
        when(twoFactorProvider.enroll("bob@example.com"))
                .thenReturn(new FactorEnrollment("SECRET32", "otpauth://totp/TasKlean:bob@example.com"));
        when(twoFactorProvider.type()).thenReturn(TwoFactorProviderType.LOCAL);
        when(secretCipher.encrypt("SECRET32")).thenReturn("enc");
    }

    @Test
    void startSetup_noExistingFactor_storesUnconfirmedFactorWithEncryptedSecret() {
        User user = user("$hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());
        stubEnrollment();

        TwoFactorSetupResponse response = twoFactorService.startSetup(USER_ID,
                TwoFactorSetupRequest.builder().password("pw").build());

        ArgumentCaptor<UserTwoFactor> captor = ArgumentCaptor.forClass(UserTwoFactor.class);
        verify(userTwoFactorRepository).save(captor.capture());
        UserTwoFactor stored = captor.getValue();
        assertThat(stored.getUser().getIdUser()).isEqualTo(USER_ID);
        assertThat(stored.getIsEnabled()).isFalse();
        // The raw secret must never reach the row - only the ciphertext.
        assertThat(stored.getSecretEncrypted()).isEqualTo("enc");
        assertThat(response.secret()).isEqualTo("SECRET32");
        assertThat(response.otpauthUri()).startsWith("otpauth://");
    }

    @Test
    void startSetup_googleOnlyAccount_doesNotRequirePassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());
        stubEnrollment();

        twoFactorService.startSetup(USER_ID, TwoFactorSetupRequest.builder().build());

        verify(userTwoFactorRepository).save(any(UserTwoFactor.class));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void startSetup_wrongPassword_throwsAndEnrollsNothing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$hash")));
        when(passwordEncoder.matches("wrong", "$hash")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.startSetup(USER_ID,
                TwoFactorSetupRequest.builder().password("wrong").build()))
                .isInstanceOf(BadCredentialsException.class);

        verify(twoFactorProvider, never()).enroll(anyString());
        verify(userTwoFactorRepository, never()).save(any());
    }

    @Test
    void startSetup_missingPasswordOnPasswordAccount_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$hash")));

        assertThatThrownBy(() -> twoFactorService.startSetup(USER_ID,
                TwoFactorSetupRequest.builder().build()))
                .isInstanceOf(BadCredentialsException.class);

        verify(twoFactorProvider, never()).enroll(anyString());
    }

    @Test
    void startSetup_alreadyEnabled_throwsBusinessRule() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$hash")));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID))
                .thenReturn(Optional.of(factor(user("$hash"), true)));

        assertThatThrownBy(() -> twoFactorService.startSetup(USER_ID,
                TwoFactorSetupRequest.builder().password("pw").build()))
                .isInstanceOf(BusinessRuleException.class);

        verify(twoFactorProvider, never()).enroll(anyString());
        verify(userTwoFactorRepository, never()).save(any());
    }

    @Test
    void startSetup_abandonedSetupExists_replacesSecretAndClearsState() {
        User user = user("$hash");
        UserTwoFactor abandoned = factor(user, false);
        abandoned.setSecretEncrypted("stale");
        abandoned.setLastUsedStep(99L);
        abandoned.setConfirmedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(abandoned));
        stubEnrollment();

        twoFactorService.startSetup(USER_ID, TwoFactorSetupRequest.builder().password("pw").build());

        // The same row is reused, with nothing left over from the abandoned attempt.
        verify(userTwoFactorRepository).save(abandoned);
        assertThat(abandoned.getSecretEncrypted()).isEqualTo("enc");
        assertThat(abandoned.getLastUsedStep()).isNull();
        assertThat(abandoned.getConfirmedAt()).isNull();
    }

    @Test
    void startSetup_inactiveAccount_throwsBusinessRule() {
        User inactive = user("$hash");
        inactive.setIsActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> twoFactorService.startSetup(USER_ID,
                TwoFactorSetupRequest.builder().password("pw").build()))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- enable ---

    @Test
    void enable_validCode_turnsOnAndReturnsRecoveryCodesAndFreshTokens() {
        User user = user("$hash");
        UserTwoFactor pending = factor(user, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(pending));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", CODE)).thenReturn(TwoFactorVerification.validAt(100L));
        when(sessionRotator.rotateCurrentSession(user))
                .thenReturn(AuthResponse.from(user, "access", "refresh"));

        TwoFactorEnableResponse response = twoFactorService.enable(USER_ID,
                TwoFactorEnableRequest.builder().code(CODE).build());

        assertThat(pending.getIsEnabled()).isTrue();
        assertThat(pending.getConfirmedAt()).isNotNull();
        // The confirming code is spent, so it can't be replayed at the next sign-in.
        assertThat(pending.getLastUsedStep()).isEqualTo(100L);
        assertThat(response.recoveryCodes()).hasSize(10).doesNotHaveDuplicates();
        assertThat(response.token()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(emailService).sendTwoFactorEnabledEmail("bob@example.com");
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.TWO_FACTOR_ENABLED), anyString());
    }

    @Test
    void enable_validCode_storesRecoveryCodesHashedOnly() {
        User user = user("$hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, false)));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", CODE)).thenReturn(TwoFactorVerification.validAt(100L));
        when(sessionRotator.rotateCurrentSession(user))
                .thenReturn(AuthResponse.from(user, "access", "refresh"));

        TwoFactorEnableResponse response = twoFactorService.enable(USER_ID,
                TwoFactorEnableRequest.builder().code(CODE).build());

        ArgumentCaptor<TwoFactorRecoveryCode> captor = ArgumentCaptor.forClass(TwoFactorRecoveryCode.class);
        verify(recoveryCodeRepository, times(10)).save(captor.capture());
        List<String> storedHashes = captor.getAllValues().stream().map(TwoFactorRecoveryCode::getCodeHash).toList();
        assertThat(storedHashes).doesNotContainAnyElementsOf(response.recoveryCodes());
        assertThat(storedHashes).containsExactlyInAnyOrderElementsOf(
                response.recoveryCodes().stream().map(this::hashOf).toList());
    }

    @Test
    void enable_wrongCode_throwsAndLeavesFactorPending() {
        User user = user("$hash");
        UserTwoFactor pending = factor(user, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(pending));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", "000000")).thenReturn(TwoFactorVerification.invalid());

        assertThatThrownBy(() -> twoFactorService.enable(USER_ID,
                TwoFactorEnableRequest.builder().code("000000").build()))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(pending.getIsEnabled()).isFalse();
        verify(userTwoFactorRepository, never()).save(any());
        verify(recoveryCodeRepository, never()).save(any());
        verify(sessionRotator, never()).rotateCurrentSession(any());
        verify(emailService, never()).sendTwoFactorEnabledEmail(anyString());
    }

    @Test
    void enable_noPendingSetup_throwsBusinessRule() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$hash")));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.enable(USER_ID,
                TwoFactorEnableRequest.builder().code(CODE).build()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void enable_alreadyEnabled_throwsBusinessRule() {
        User user = user("$hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));

        assertThatThrownBy(() -> twoFactorService.enable(USER_ID,
                TwoFactorEnableRequest.builder().code(CODE).build()))
                .isInstanceOf(BusinessRuleException.class);

        verify(twoFactorProvider, never()).verify(any(), any());
    }

    // --- disable ---

    @Test
    void disable_validAuthenticatorCode_removesFactorAndCodesAndRotatesSession() {
        User user = user("$hash");
        UserTwoFactor enabled = factor(user, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(enabled));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", CODE)).thenReturn(TwoFactorVerification.validAt(100L));
        when(sessionRotator.rotateCurrentSession(user))
                .thenReturn(AuthResponse.from(user, "access", "refresh"));

        AuthResponse response = twoFactorService.disable(USER_ID,
                TwoFactorConfirmRequest.builder().password("pw").code(CODE).build());

        assertThat(response.getToken()).isEqualTo("access");
        verify(recoveryCodeRepository).deleteByUserIdUser(USER_ID);
        verify(userTwoFactorRepository).delete(enabled);
        verify(emailService).sendTwoFactorDisabledEmail("bob@example.com");
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.TWO_FACTOR_DISABLED), anyString());
    }

    @Test
    void disable_validRecoveryCode_spendsTheCodeAndTurnsTwoFactorOff() {
        User user = user("$hash");
        UserTwoFactor enabled = factor(user, true);
        TwoFactorRecoveryCode stored = TwoFactorRecoveryCode.builder()
                .idTwoFactorRecoveryCode(3L).user(user).codeHash(hashOf("abcd-efgh")).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(enabled));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", "abcd-efgh")).thenReturn(TwoFactorVerification.invalid());
        when(recoveryCodeRepository.findByUserIdUserAndCodeHashAndUsedAtIsNull(USER_ID, hashOf("abcd-efgh")))
                .thenReturn(Optional.of(stored));
        when(sessionRotator.rotateCurrentSession(user))
                .thenReturn(AuthResponse.from(user, "access", "refresh"));

        twoFactorService.disable(USER_ID,
                TwoFactorConfirmRequest.builder().password("pw").code("abcd-efgh").build());

        assertThat(stored.getUsedAt()).isNotNull();
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.TWO_FACTOR_RECOVERY_USED), anyString());
        verify(userTwoFactorRepository).delete(enabled);
    }

    @Test
    void disable_wrongPassword_throwsAndKeepsFactor() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("$hash")));
        when(passwordEncoder.matches("wrong", "$hash")).thenReturn(false);

        // The code tells the client which of the two fields to flag.
        assertThatThrownBy(() -> twoFactorService.disable(USER_ID,
                TwoFactorConfirmRequest.builder().password("wrong").code(CODE).build()))
                .isInstanceOf(AuthFailureException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCodes.PASSWORD_INVALID);

        verify(userTwoFactorRepository, never()).delete(any());
        verify(recoveryCodeRepository, never()).deleteByUserIdUser(any());
    }

    @Test
    void disable_invalidCode_throwsAndKeepsFactor() {
        User user = user("$hash");
        UserTwoFactor enabled = factor(user, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(enabled));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", "000000")).thenReturn(TwoFactorVerification.invalid());
        when(recoveryCodeRepository.findByUserIdUserAndCodeHashAndUsedAtIsNull(USER_ID, hashOf("000000")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.disable(USER_ID,
                TwoFactorConfirmRequest.builder().password("pw").code("000000").build()))
                .isInstanceOf(AuthFailureException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCodes.TWO_FACTOR_CODE_INVALID);

        verify(userTwoFactorRepository, never()).delete(any());
        verify(sessionRotator, never()).rotateCurrentSession(any());
    }

    @Test
    void disable_twoFactorNotEnabled_throwsBusinessRule() {
        User user = user("$hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        // A pending setup is not an enabled factor, so there is nothing to turn off.
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, false)));

        assertThatThrownBy(() -> twoFactorService.disable(USER_ID,
                TwoFactorConfirmRequest.builder().password("pw").code(CODE).build()))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- regenerateRecoveryCodes ---

    @Test
    void regenerateRecoveryCodes_validCode_replacesTheWholeSetAndKeepsSessions() {
        User user = user("$hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.of(factor(user, true)));
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", CODE)).thenReturn(TwoFactorVerification.validAt(100L));

        RecoveryCodesResponse response = twoFactorService.regenerateRecoveryCodes(USER_ID,
                TwoFactorConfirmRequest.builder().password("pw").code(CODE).build());

        assertThat(response.recoveryCodes()).hasSize(10).doesNotHaveDuplicates();
        verify(recoveryCodeRepository).deleteByUserIdUser(USER_ID);
        verify(recoveryCodeRepository, times(10)).save(any(TwoFactorRecoveryCode.class));
        // Replacing codes doesn't change how the account signs in, so sessions stay.
        verify(sessionRotator, never()).rotateCurrentSession(any());
        verify(emailService).sendRecoveryCodesRegeneratedEmail("bob@example.com");
        verify(auditLogService).recordAccountEvent(eq(USER_ID), eq(AuditAction.RECOVERY_CODES_REGENERATED), anyString());
    }

    @Test
    void regenerateRecoveryCodes_twoFactorNotEnabled_throwsBusinessRule() {
        User user = user("$hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$hash")).thenReturn(true);
        when(userTwoFactorRepository.findByUserIdUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.regenerateRecoveryCodes(USER_ID,
                TwoFactorConfirmRequest.builder().password("pw").code(CODE).build()))
                .isInstanceOf(BusinessRuleException.class);

        verify(recoveryCodeRepository, never()).save(any());
    }

    // --- consumeCode (shared with the sign-in challenge) ---

    @Test
    void consumeCode_newerTimeStep_recordsTheStepAndReportsTotp() {
        User user = user("$hash");
        UserTwoFactor enabled = factor(user, true);
        enabled.setLastUsedStep(99L);
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", CODE)).thenReturn(TwoFactorVerification.validAt(100L));

        TwoFactorCodeOutcome outcome = twoFactorService.consumeCode(user, enabled, CODE);

        assertThat(outcome).isEqualTo(TwoFactorCodeOutcome.TOTP);
        assertThat(enabled.getLastUsedStep()).isEqualTo(100L);
        verify(userTwoFactorRepository).save(enabled);
    }

    @Test
    void consumeCode_replayedTimeStep_reportsInvalid() {
        User user = user("$hash");
        UserTwoFactor enabled = factor(user, true);
        enabled.setLastUsedStep(100L);
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", CODE)).thenReturn(TwoFactorVerification.validAt(100L));

        TwoFactorCodeOutcome outcome = twoFactorService.consumeCode(user, enabled, CODE);

        // The code is arithmetically correct but already spent inside its skew window.
        assertThat(outcome).isEqualTo(TwoFactorCodeOutcome.INVALID);
        verify(userTwoFactorRepository, never()).save(any());
        verify(recoveryCodeRepository, never()).findByUserIdUserAndCodeHashAndUsedAtIsNull(any(), anyString());
    }

    @Test
    void consumeCode_alreadyUsedRecoveryCode_reportsInvalid() {
        User user = user("$hash");
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", "abcd-efgh")).thenReturn(TwoFactorVerification.invalid());
        when(recoveryCodeRepository.findByUserIdUserAndCodeHashAndUsedAtIsNull(USER_ID, hashOf("abcd-efgh")))
                .thenReturn(Optional.empty());

        TwoFactorCodeOutcome outcome = twoFactorService.consumeCode(user, factor(user, true), "abcd-efgh");

        assertThat(outcome).isEqualTo(TwoFactorCodeOutcome.INVALID);
        verify(auditLogService, never()).recordAccountEvent(any(), any(), anyString());
    }

    @Test
    void consumeCode_recoveryCodeInAnyFormatting_matchesTheStoredHash() {
        User user = user("$hash");
        TwoFactorRecoveryCode stored = TwoFactorRecoveryCode.builder()
                .idTwoFactorRecoveryCode(3L).user(user).codeHash(hashOf("abcd-efgh")).build();
        when(secretCipher.decrypt("enc")).thenReturn("SECRET32");
        when(twoFactorProvider.verify("SECRET32", " ABCDEFGH ")).thenReturn(TwoFactorVerification.invalid());
        when(recoveryCodeRepository.findByUserIdUserAndCodeHashAndUsedAtIsNull(USER_ID, hashOf("abcd-efgh")))
                .thenReturn(Optional.of(stored));

        // Upper case, padded and without the separator - the same code the user was shown.
        TwoFactorCodeOutcome outcome = twoFactorService.consumeCode(user, factor(user, true), " ABCDEFGH ");

        assertThat(outcome).isEqualTo(TwoFactorCodeOutcome.RECOVERY);
        assertThat(stored.getUsedAt()).isNotNull();
    }

    @Test
    void consumeCode_blankCode_reportsInvalidWithoutCallingTheProvider() {
        User user = user("$hash");

        TwoFactorCodeOutcome outcome = twoFactorService.consumeCode(user, factor(user, true), "  ");

        assertThat(outcome).isEqualTo(TwoFactorCodeOutcome.INVALID);
        verify(twoFactorProvider, never()).verify(any(), any());
        verify(secretCipher, never()).decrypt(anyString());
    }
}
