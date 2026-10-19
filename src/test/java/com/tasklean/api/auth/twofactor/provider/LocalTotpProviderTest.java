package com.tasklean.api.auth.twofactor.provider;

import com.atlassian.onetime.core.HMACDigest;
import com.atlassian.onetime.core.OTPLength;
import com.atlassian.onetime.core.TOTPGenerator;
import com.atlassian.onetime.model.TOTPSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the local TOTP adapter against the RFC 6238 test vectors. The algorithm itself is the
 * library's; what these prove is that it is wired up with the step size, digit count, digest and
 * skew window an authenticator app expects — the realistic failure mode for a thin adapter.
 */
class LocalTotpProviderTest {

    // RFC 6238 Appendix B: the 20-byte ASCII seed "12345678901234567890", Base32-encoded.
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final int STEP = 30;

    private LocalTotpProvider providerAt(long epochSecond) {
        return new LocalTotpProvider(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }

    // --- RFC 6238 vectors (the published 8-digit values, truncated to the 6 digits apps show) ---

    @ParameterizedTest
    @CsvSource({
            "59,            287082",
            "1111111109,    081804",
            "1111111111,    050471",
            "1234567890,    005924",
            "2000000000,    279037",
            "20000000000,   353130"
    })
    void verify_rfc6238Vector_acceptsTheCodeForThatTime(long epochSecond, String code) {
        TwoFactorVerification result = providerAt(epochSecond).verify(RFC_SECRET, code);

        assertThat(result.valid()).isTrue();
        assertThat(result.timeStep()).isEqualTo(epochSecond / STEP);
    }

    @Test
    void verify_wrongCodeForThatTime_isRejected() {
        TwoFactorVerification result = providerAt(59).verify(RFC_SECRET, "287083");

        assertThat(result.valid()).isFalse();
        assertThat(result.timeStep()).isNull();
    }

    // --- clock skew: one step either side, and no further ---

    @Test
    void verify_codeFromThePreviousStep_isAcceptedAndReportsThatStep() {
        // The 1111111109 code, submitted one step later.
        TwoFactorVerification result = providerAt(1111111109L + STEP).verify(RFC_SECRET, "081804");

        assertThat(result.valid()).isTrue();
        // The earlier step, not the current one - this is what makes replay detectable.
        assertThat(result.timeStep()).isEqualTo(1111111109L / STEP);
    }

    @Test
    void verify_codeFromTheNextStep_isAccepted() {
        TwoFactorVerification result = providerAt(1111111109L - STEP).verify(RFC_SECRET, "081804");

        assertThat(result.valid()).isTrue();
        assertThat(result.timeStep()).isEqualTo(1111111109L / STEP);
    }

    @Test
    void verify_codeTwoStepsInThePast_isRejected() {
        TwoFactorVerification result = providerAt(1111111109L + 2 * STEP).verify(RFC_SECRET, "081804");

        assertThat(result.valid()).isFalse();
    }

    @Test
    void verify_codeTwoStepsInTheFuture_isRejected() {
        TwoFactorVerification result = providerAt(1111111109L - 2 * STEP).verify(RFC_SECRET, "081804");

        assertThat(result.valid()).isFalse();
    }

    // --- input handling: never throws, whatever the user or the database hands it ---

    @Test
    void verify_codeWithSpaces_isAccepted() {
        // Authenticator apps display "081 804".
        TwoFactorVerification result = providerAt(1111111109L).verify(RFC_SECRET, " 081 804 ");

        assertThat(result.valid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "1234567", "abcdef", "08180a"})
    void verify_malformedCode_isRejectedWithoutThrowing(String code) {
        assertThat(providerAt(1111111109L).verify(RFC_SECRET, code).valid()).isFalse();
    }

    @Test
    void verify_nullCodeOrCredential_isRejected() {
        LocalTotpProvider provider = providerAt(1111111109L);

        assertThat(provider.verify(RFC_SECRET, null).valid()).isFalse();
        assertThat(provider.verify(null, "081804").valid()).isFalse();
    }

    @Test
    void verify_credentialThatIsNotBase32_isRejectedWithoutThrowing() {
        TwoFactorVerification result = providerAt(1111111109L).verify("not a secret!", "081804");

        assertThat(result.valid()).isFalse();
    }

    @Test
    void verify_differentSecret_doesNotAcceptAnotherSecretsCode() {
        String otherSecret = providerAt(0).enroll("a@b.com").credential();

        assertThat(providerAt(1111111109L).verify(otherSecret, "081804").valid()).isFalse();
    }

    // --- enrollment ---

    @Test
    void enroll_producesABase32SecretAndAnOtpauthUriForTheAccount() {
        FactorEnrollment enrollment = providerAt(0).enroll("alice@example.com");

        // 20 random bytes Base32-encoded, which is what authenticator apps expect.
        assertThat(enrollment.credential()).matches("[A-Z2-7]{32}");
        assertThat(enrollment.otpauthUri())
                .startsWith("otpauth://totp/TasKlean:alice%40example.com")
                .contains("secret=" + enrollment.credential())
                .contains("issuer=TasKlean");
    }

    @Test
    void enroll_generatesACodeThatVerifiesAgainstItsOwnSecret() {
        LocalTotpProvider provider = providerAt(1111111109L);
        FactorEnrollment enrollment = provider.enroll("alice@example.com");

        // Round-trip through the library: whatever enroll() hands out must verify at the same instant.
        String code = codeFor(enrollment.credential());
        assertThat(provider.verify(enrollment.credential(), code).valid()).isTrue();
    }

    @Test
    void enroll_twice_producesDifferentSecrets() {
        LocalTotpProvider provider = providerAt(0);

        assertThat(provider.enroll("a@b.com").credential())
                .isNotEqualTo(provider.enroll("a@b.com").credential());
    }

    // Generates the current code for a secret the way a phone would, via the same library.
    private String codeFor(String base32Secret) {
        return new TOTPGenerator(Clock.fixed(Instant.ofEpochSecond(1111111109L), ZoneOffset.UTC),
                0, STEP, OTPLength.SIX, HMACDigest.SHA1)
                .generateCurrent(TOTPSecret.Companion.fromBase32EncodedString(base32Secret))
                .getValue();
    }
}
