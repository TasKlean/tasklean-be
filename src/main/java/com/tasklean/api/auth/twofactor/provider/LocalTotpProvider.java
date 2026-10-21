package com.tasklean.api.auth.twofactor.provider;

import com.atlassian.onetime.core.HMACDigest;
import com.atlassian.onetime.core.OTPLength;
import com.atlassian.onetime.core.TOTP;
import com.atlassian.onetime.core.TOTPGenerator;
import com.atlassian.onetime.model.EmailAddress;
import com.atlassian.onetime.model.Issuer;
import com.atlassian.onetime.model.TOTPSecret;
import com.atlassian.onetime.service.DefaultTOTPService;
import com.atlassian.onetime.service.RandomSecretProvider;
import com.atlassian.onetime.service.TOTPConfiguration;
import com.atlassian.onetime.service.TOTPService;
import com.atlassian.onetime.service.TOTPVerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Verifies RFC 6238 TOTP codes in-process against a secret we store ourselves. Every detail of the
 * algorithm (step size, digits, skew) is confined to this adapter, which is the point of the port.
 */
@Slf4j
@Component
public class LocalTotpProvider implements TwoFactorProvider {

    // Authenticator apps assume 30-second steps, 6 digits and SHA-1; these are not ours to tune.
    private static final int TIME_STEP_SECONDS = 30;
    private static final int START_TIME = 0;

    // One step either side, so a phone clock drifting by up to 30s still works. Each extra step
    // widens the window an attacker can guess in, so this stays at the minimum that is usable.
    private static final int ALLOWED_PAST_STEPS = 1;
    private static final int ALLOWED_FUTURE_STEPS = 1;

    private static final String ISSUER = "TasKlean";
    private static final int CODE_LENGTH = 6;

    private final Clock clock;
    private final TOTPService totpService;
    private final RandomSecretProvider secretProvider = new RandomSecretProvider();

    // The library's service is built here rather than exposed as a bean: it is an implementation
    // detail of this adapter, and a hosted provider would have no use for it.
    public LocalTotpProvider(Clock clock) {
        this.clock = clock;
        this.totpService = new DefaultTOTPService(
                new TOTPGenerator(clock, START_TIME, TIME_STEP_SECONDS, OTPLength.SIX, HMACDigest.SHA1),
                new TOTPConfiguration(ALLOWED_PAST_STEPS, ALLOWED_FUTURE_STEPS));
    }

    @Override
    public TwoFactorProviderType type() {
        return TwoFactorProviderType.LOCAL;
    }

    @Override
    public FactorEnrollment enroll(String accountEmail) {
        TOTPSecret secret = secretProvider.generateSecret();
        String otpauthUri = totpService
                .generateTOTPUrl(secret, new EmailAddress(accountEmail), new Issuer(ISSUER))
                .toString();

        // Never log the secret or the URI that carries it.
        return new FactorEnrollment(secret.getBase32Encoded(), otpauthUri);
    }

    @Override
    public TwoFactorVerification verify(String credential, String code) {
        String normalized = normalize(code);
        if (credential == null || normalized == null) {
            return TwoFactorVerification.invalid();
        }

        try {
            TOTPSecret secret = TOTPSecret.Companion.fromBase32EncodedString(credential);
            if (totpService.verify(new TOTP(normalized), secret) instanceof TOTPVerificationResult.Success success) {
                // The library reports the match as an offset from the current step; the caller needs
                // the absolute step to tell a replay from a fresh code.
                return TwoFactorVerification.validAt(currentTimeStep() + success.getIndex());
            }
        } catch (RuntimeException e) {
            // A stored credential that no longer decodes is a failed verification, not a 500.
            log.warn("TOTP verification could not run: {}", e.getClass().getSimpleName());
        }
        return TwoFactorVerification.invalid();
    }

    // Authenticator apps display codes as "123 456", so whitespace is stripped before matching.
    private String normalize(String code) {
        if (code == null) {
            return null;
        }
        String stripped = code.replace(" ", "");
        return stripped.length() == CODE_LENGTH && stripped.chars().allMatch(Character::isDigit)
                ? stripped
                : null;
    }

    private long currentTimeStep() {
        return clock.instant().getEpochSecond() / TIME_STEP_SECONDS;
    }
}
