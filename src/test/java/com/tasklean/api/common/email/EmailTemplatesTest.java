package com.tasklean.api.common.email;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates("http://localhost:3000");

    /** Every template, for the invariants that have to hold across all of them. */
    private List<EmailContent> all() {
        return List.of(
                templates.verificationCode("bob@example.com", "123456", Delivery.ESSENTIAL),
                templates.welcome(),
                templates.registrationAttempt(),
                templates.passwordReset("tok-ABC123"),
                templates.googleOnlyReset(),
                templates.passwordChanged(),
                templates.passwordSet(),
                templates.googleLinked(),
                templates.googleUnlinked(),
                templates.accountDeactivated(),
                templates.twoFactorEnabled(),
                templates.twoFactorDisabled(),
                templates.recoveryCodesRegenerated(),
                templates.recoveryCodeUsed(),
                templates.newSignIn("Chrome on Windows", "203.0.113.5"));
    }

    /** Asserts a phrase reaches both parts — the guard against the two drifting apart. */
    private void assertBothParts(EmailContent content, String... phrases) {
        assertThat(content.text()).contains(phrases);
        assertThat(content.html()).contains(phrases);
    }

    // --- invariants ---

    @Test
    void everyTemplate_rendersAFullDocumentAndAReadableTextPart() {
        for (EmailContent content : all()) {
            assertThat(content.subject()).startsWith("TasKlean - ");
            assertThat(content.html()).startsWith("<!DOCTYPE html>");
            // A template that forgot to compose would leave markup in the fallback part.
            assertThat(content.text()).isNotBlank().doesNotContain("<");
        }
    }

    // --- links ---

    @Test
    void verificationCode_buildsPrefilledLinkWithEncodedEmailAndCode() {
        // Trailing slash on the base URL must not produce a double slash in the link.
        EmailTemplates withSlash = new EmailTemplates("http://localhost:3000/");

        EmailContent content = withSlash.verificationCode("a+b@example.com", "123456", Delivery.ESSENTIAL);

        // Email is URL-encoded (+ -> %2B, @ -> %40); exact param names email/code.
        assertThat(content.text())
                .contains("http://localhost:3000/verify-email?email=a%2Bb%40example.com&code=123456");
        // Same link in the HTML part, with the query separator escaped for markup.
        assertThat(content.html())
                .contains("http://localhost:3000/verify-email?email=a%2Bb%40example.com&amp;code=123456");
        assertBothParts(content, "123456");
    }

    @Test
    void passwordReset_buildsResetLinkWithToken() {
        EmailContent content = templates.passwordReset("tok-ABC123");

        assertBothParts(content,
                "http://localhost:3000/reset-password?token=tok-ABC123", "expires in 15 minutes");
    }

    @Test
    void googleOnlyReset_mentionsGoogleAndCarriesNoLink() {
        EmailContent content = templates.googleOnlyReset();

        assertBothParts(content, "Google");
        assertThat(content.text()).doesNotContain("/reset-password?token=");
        assertThat(content.html()).doesNotContain("/reset-password?token=");
    }

    @Test
    void newSignIn_includesDeviceAndIp() {
        assertBothParts(templates.newSignIn("Chrome on Windows", "203.0.113.5"),
                "Chrome on Windows", "203.0.113.5");
    }

    @Test
    void newSignIn_blankIp_omitsTheIpRow() {
        EmailContent content = templates.newSignIn("Chrome on Windows", null);

        assertBothParts(content, "Chrome on Windows");
        assertThat(content.text()).doesNotContain("IP address");
        assertThat(content.html()).doesNotContain("IP address");
    }

    // --- copy ---

    @Test
    void welcome_greetsTheNewAccountAndLinksToTheApp() {
        assertBothParts(templates.welcome(),
                "Welcome to TasKlean", "http://localhost:3000/app");
    }

    @Test
    void registrationAttempt_tellsTheOwnerAnAccountExists() {
        assertBothParts(templates.registrationAttempt(), "account already exists");
    }

    @Test
    void passwordChanged_namesTheChange() {
        assertBothParts(templates.passwordChanged(), "password was just changed");
    }

    @Test
    void passwordSet_namesTheAddedPassword() {
        assertBothParts(templates.passwordSet(), "password was just added");
    }

    @Test
    void googleLinked_namesTheLink() {
        assertBothParts(templates.googleLinked(), "Google account was just linked");
    }

    @Test
    void googleUnlinked_namesTheRemoval() {
        assertBothParts(templates.googleUnlinked(), "Google sign-in was just removed");
    }

    @Test
    void accountDeactivated_namesTheDeactivation() {
        assertBothParts(templates.accountDeactivated(), "account was just deactivated");
    }

    @Test
    void twoFactorEnabled_namesTheChangeAndPointsAtRecoveryCodes() {
        assertBothParts(templates.twoFactorEnabled(),
                "Two-factor authentication was just turned on", "recovery codes");
    }

    @Test
    void twoFactorDisabled_namesTheWeakenedSignIn() {
        assertBothParts(templates.twoFactorDisabled(), "just turned off");
    }

    @Test
    void recoveryCodesRegenerated_saysThePreviousCodesAreDead() {
        assertBothParts(templates.recoveryCodesRegenerated(), "previous codes no longer work");
    }

    @Test
    void recoveryCodeUsed_namesTheAppFreeSignIn() {
        assertBothParts(templates.recoveryCodeUsed(), "recovery code");
    }

    // --- delivery policy ---

    @Test
    void registrationAttempt_isEssential() {
        // Nothing is created, but it has to fail alongside the verification code on register's
        // other branch, or the difference reveals whether the address is already registered.
        assertThat(templates.registrationAttempt().delivery()).isEqualTo(Delivery.ESSENTIAL);
    }

    @Test
    void verificationCode_takesThePolicyFromItsCaller() {
        // The only template with two policies: registration cannot succeed without the code, while
        // a resend must stay indistinguishable from the no-op an unknown address gets.
        assertThat(templates.verificationCode("bob@example.com", "123456", Delivery.ESSENTIAL).delivery())
                .isEqualTo(Delivery.ESSENTIAL);
        assertThat(templates.verificationCode("bob@example.com", "123456", Delivery.ADVISORY).delivery())
                .isEqualTo(Delivery.ADVISORY);
    }

    @Test
    void welcome_isAdvisory() {
        // It must be: verifyEmail is transactional, so an essential failure here would roll the
        // verification back and a mail outage would stop people finishing sign-up.
        assertThat(templates.welcome().delivery()).isEqualTo(Delivery.ADVISORY);
    }

    @Test
    void passwordReset_isAdvisory() {
        // Advisory despite the link being the whole point: forgot-password answers an unknown
        // address with a silent no-op, so failing here would reveal which addresses exist.
        assertThat(templates.passwordReset("tok-ABC123").delivery()).isEqualTo(Delivery.ADVISORY);
    }

    @Test
    void notificationTemplates_areAdvisory() {
        // Each reports something that already happened, so losing the email must not undo it.
        assertThat(templates.googleOnlyReset().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.passwordChanged().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.passwordSet().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.googleLinked().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.googleUnlinked().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.accountDeactivated().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.twoFactorEnabled().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.twoFactorDisabled().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.recoveryCodesRegenerated().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.recoveryCodeUsed().delivery()).isEqualTo(Delivery.ADVISORY);
        assertThat(templates.newSignIn("Chrome on Windows", null).delivery()).isEqualTo(Delivery.ADVISORY);
    }
}
