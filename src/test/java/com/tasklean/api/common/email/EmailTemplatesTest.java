package com.tasklean.api.common.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates("http://localhost:3000");

    // --- links ---

    @Test
    void verificationCode_buildsPrefilledLinkWithEncodedEmailAndCode() {
        // Trailing slash on the base URL must not produce a double slash in the link.
        EmailTemplates withSlash = new EmailTemplates("http://localhost:3000/");

        EmailContent content = withSlash.verificationCode("a+b@example.com", "123456", Delivery.ESSENTIAL);

        // Email is URL-encoded (+ -> %2B, @ -> %40); exact param names email/code.
        assertThat(content.body())
                .contains("http://localhost:3000/verify-email?email=a%2Bb%40example.com&code=123456")
                .contains("enter this code manually: 123456");
    }

    @Test
    void passwordReset_buildsResetLinkWithToken() {
        EmailContent content = templates.passwordReset("tok-ABC123");

        assertThat(content.body())
                .contains("http://localhost:3000/reset-password?token=tok-ABC123")
                .contains("expires in 15 minutes");
    }

    @Test
    void googleOnlyReset_mentionsGoogleAndCarriesNoLink() {
        EmailContent content = templates.googleOnlyReset();

        assertThat(content.body()).contains("Google").doesNotContain("/reset-password?token=");
    }

    @Test
    void newSignIn_includesDeviceAndIp() {
        EmailContent content = templates.newSignIn("Chrome on Windows", "203.0.113.5");

        assertThat(content.body()).contains("Chrome on Windows").contains("203.0.113.5");
    }

    @Test
    void newSignIn_blankIp_omitsIpClause() {
        EmailContent content = templates.newSignIn("Chrome on Windows", null);

        assertThat(content.body()).contains("Chrome on Windows").doesNotContain("IP ");
    }

    // --- copy ---

    @Test
    void registrationAttempt_tellsTheOwnerAnAccountExists() {
        assertThat(templates.registrationAttempt().body()).contains("account already exists");
    }

    @Test
    void passwordChanged_namesTheChange() {
        assertThat(templates.passwordChanged().body()).contains("password was just changed");
    }

    @Test
    void passwordSet_namesTheAddedPassword() {
        assertThat(templates.passwordSet().body()).contains("password was just added");
    }

    @Test
    void googleLinked_namesTheLink() {
        assertThat(templates.googleLinked().body()).contains("Google account was just linked");
    }

    @Test
    void googleUnlinked_namesTheRemoval() {
        assertThat(templates.googleUnlinked().body()).contains("Google sign-in was just removed");
    }

    @Test
    void accountDeactivated_namesTheDeactivation() {
        assertThat(templates.accountDeactivated().body()).contains("account was just deactivated");
    }

    @Test
    void twoFactorEnabled_namesTheChangeAndPointsAtRecoveryCodes() {
        assertThat(templates.twoFactorEnabled().body())
                .contains("Two-factor authentication was just turned on")
                .contains("recovery codes");
    }

    @Test
    void twoFactorDisabled_namesTheWeakenedSignIn() {
        assertThat(templates.twoFactorDisabled().body()).contains("just turned off");
    }

    @Test
    void recoveryCodesRegenerated_saysThePreviousCodesAreDead() {
        assertThat(templates.recoveryCodesRegenerated().body()).contains("previous codes no longer work");
    }

    @Test
    void recoveryCodeUsed_namesTheAppFreeSignIn() {
        assertThat(templates.recoveryCodeUsed().body()).contains("recovery code");
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
