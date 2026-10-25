package com.tasklean.api.common.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Every email the app sends, as composed content. Each template owns both its copy and its
 * {@link Delivery} policy, so the question "may this email be lost?" is answered once, next to the
 * words, rather than re-decided by each caller.
 *
 * <p>Holds the frontend base URL because two templates carry links into it. Pure functions — nothing
 * here touches the mail provider; {@link EmailService} does the sending.
 */
@Component
public class EmailTemplates {

    private final String baseUrl;

    /**
     * @param baseUrl the frontend origin links point at; any trailing slash is stripped so a link
     *                never ends up with a double slash
     */
    public EmailTemplates(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = StringUtils.trimTrailingCharacter(baseUrl, '/');
    }

    /**
     * The email-verification message: a link to the frontend verify page (prefilled with the email
     * and code) plus the raw code as a manual-entry fallback. The link only lands on the page;
     * verification itself happens when the recipient clicks there, so a mail scanner prefetching the
     * link can't spend the single-use code.
     *
     * <p>The only template whose caller chooses the policy: registration cannot succeed without the
     * code, but a resend must stay indistinguishable from the no-op it performs for an unknown or
     * already-verified address.
     *
     * @param to       the recipient, which the verify link carries so the page can prefill it
     * @param code     the verification code to send
     * @param delivery whether a failed send should fail the caller's request
     * @return the composed message
     */
    public EmailContent verificationCode(String to, String code, Delivery delivery) {
        String link = baseUrl + "/verify-email?email=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                + "&code=" + code;
        return new EmailContent("TasKlean - Verify your email",
                "Verify your email by opening this link:\n" + link
                        + "\n\nOr enter this code manually: " + code
                        + "\n\nThis code expires in 5 minutes.", delivery);
    }

    /**
     * Tells the owner of an existing account that someone tried to register with their email.
     * Sent instead of revealing account existence in the API response (anti-enumeration).
     *
     * <p>Essential although it creates nothing: it has to fail alongside the verification code on
     * register's other branch, or the difference between the two reveals which branch ran.
     *
     * @return the composed message
     */
    public EmailContent registrationAttempt() {
        return new EmailContent("TasKlean - Account already exists", """
                Someone tried to create a TasKlean account with this email, but an account already exists.

                If this was you, just sign in instead - you can use your password or Google.
                If it wasn't you, you can safely ignore this email.""", Delivery.ESSENTIAL);
    }

    /**
     * A password-reset link to the frontend reset page, carrying the single-use token.
     *
     * <p>Advisory even though the link is the whole point: forgot-password answers an unknown
     * address with a silent no-op, so failing here would reveal which addresses exist.
     *
     * @param token the raw reset token (only ever sent here, never stored in plaintext)
     * @return the composed message
     */
    public EmailContent passwordReset(String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        return new EmailContent("TasKlean - Reset your password", """
                Reset your password by opening this link:
                %s

                This link expires in 15 minutes and can be used once.
                If you didn't request this, you can safely ignore this email - your password stays unchanged.""".formatted(link),
                Delivery.ADVISORY);
    }

    /**
     * Tells a Google-only account holder that there's no password to reset (sent in place of a
     * reset link so the API response can stay neutral — anti-enumeration).
     *
     * @return the composed message
     */
    public EmailContent googleOnlyReset() {
        return new EmailContent("TasKlean - Password reset", """
                You asked to reset your TasKlean password, but this account signs in with Google.

                There's no password to reset - just use "Continue with Google" to sign in.
                If you didn't request this, you can safely ignore this email.""", Delivery.ADVISORY);
    }

    /**
     * Confirms to the account owner that their password was just changed, so an unauthorized
     * reset is noticeable.
     *
     * @return the composed message
     */
    public EmailContent passwordChanged() {
        return new EmailContent("TasKlean - Your password was changed", """
                Your TasKlean password was just changed, and you've been signed out on all devices.

                If this was you, you can ignore this email.
                If it wasn't you, reset your password immediately and contact support - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms to a (previously Google-only) account holder that a password was just added, so an
     * unauthorized change is noticeable.
     *
     * @return the composed message
     */
    public EmailContent passwordSet() {
        return new EmailContent("TasKlean - A password was added to your account", """
                A password was just added to your TasKlean account. You can now sign in with your
                email and password, or keep using Google. For your security, your other sessions
                have been signed out.

                If this was you, you can ignore this email.
                If it wasn't you, reset your password immediately and contact support - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms that a Google account was just linked, so an unauthorized link is noticeable.
     *
     * @return the composed message
     */
    public EmailContent googleLinked() {
        return new EmailContent("TasKlean - Google was linked to your account", """
                A Google account was just linked to your TasKlean account, so you can now sign in with Google.

                If this was you, you can ignore this email.
                If it wasn't you, unlink Google and change your password from your account settings - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms that a Google account was just unlinked, so an unauthorized change is noticeable.
     *
     * @return the composed message
     */
    public EmailContent googleUnlinked() {
        return new EmailContent("TasKlean - Google was unlinked from your account", """
                Google sign-in was just removed from your TasKlean account. You'll now sign in with your email and password.

                If this was you, you can ignore this email.
                If it wasn't you, change your password immediately and contact support - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms that the account was just deactivated, so an unauthorized deactivation is noticeable.
     *
     * @return the composed message
     */
    public EmailContent accountDeactivated() {
        return new EmailContent("TasKlean - Your account was deactivated", """
                Your TasKlean account was just deactivated and you've been signed out everywhere.

                If this was you, no further action is needed.
                If it wasn't you, contact support right away - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms that two-factor authentication was just turned on, so an unauthorized change is
     * noticeable. Never carries the secret or any recovery code.
     *
     * @return the composed message
     */
    public EmailContent twoFactorEnabled() {
        return new EmailContent("TasKlean - Two-factor authentication is on", """
                Two-factor authentication was just turned on for your TasKlean account. From now on
                you'll enter a code from your authenticator app when you sign in, and your other
                sessions have been signed out.

                Keep your recovery codes somewhere safe - they're the only way back in if you lose your phone.

                If this wasn't you, contact support right away - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms that two-factor authentication was just turned off, so an unauthorized change is
     * noticeable.
     *
     * @return the composed message
     */
    public EmailContent twoFactorDisabled() {
        return new EmailContent("TasKlean - Two-factor authentication is off", """
                Two-factor authentication was just turned off for your TasKlean account, and your
                other sessions have been signed out. You'll now sign in with your password alone.

                If this wasn't you, turn it back on and change your password immediately - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Confirms that a fresh set of recovery codes was issued, which invalidated the previous set.
     *
     * @return the composed message
     */
    public EmailContent recoveryCodesRegenerated() {
        return new EmailContent("TasKlean - New recovery codes were generated", """
                A new set of two-factor recovery codes was just generated for your TasKlean account.
                Your previous codes no longer work.

                If this wasn't you, contact support right away - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Alerts the account owner that a recovery code was just used to sign in, which is the one
     * two-factor path that needs no phone - so an unrecognised use must be noticeable.
     *
     * @return the composed message
     */
    public EmailContent recoveryCodeUsed() {
        return new EmailContent("TasKlean - A recovery code was used to sign in", """
                Someone signed in to your TasKlean account with a two-factor recovery code instead of
                your authenticator app. That code is now used up.

                If this was you, no further action is needed - you can generate a fresh set of codes
                in your account settings.
                If it wasn't you, change your password and generate new recovery codes right away - your account may be compromised.""",
                Delivery.ADVISORY);
    }

    /**
     * Alerts the account owner that their account was signed into from a new device, so an
     * unrecognised sign-in is noticeable. Carries only the device label and IP, never credentials.
     *
     * @param deviceName the friendly device label (e.g. "Chrome on Windows")
     * @param ipAddress  the IP the sign-in came from; omitted from the message when null/blank
     * @return the composed message
     */
    public EmailContent newSignIn(String deviceName, String ipAddress) {
        String fromIp = (ipAddress != null && !ipAddress.isBlank()) ? " (IP " + ipAddress + ")" : "";
        return new EmailContent("TasKlean - New sign-in to your account", """
                Your TasKlean account was just signed into from a new device:
                %s%s

                If this was you, you can ignore this email.
                If it wasn't you, change your password and sign out everywhere from your account settings.""".formatted(deviceName, fromIp),
                Delivery.ADVISORY);
    }
}
