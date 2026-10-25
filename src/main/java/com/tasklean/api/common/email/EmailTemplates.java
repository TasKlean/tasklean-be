package com.tasklean.api.common.email;

import com.tasklean.api.common.email.html.EmailBlock;
import com.tasklean.api.common.email.html.EmailLayout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.tasklean.api.common.email.html.EmailBlock.button;
import static com.tasklean.api.common.email.html.EmailBlock.code;
import static com.tasklean.api.common.email.html.EmailBlock.dataRow;
import static com.tasklean.api.common.email.html.EmailBlock.heading;
import static com.tasklean.api.common.email.html.EmailBlock.note;
import static com.tasklean.api.common.email.html.EmailBlock.paragraph;

/**
 * Every email the app sends, declared as a list of {@link EmailBlock}s. Each template owns its
 * copy, its structure and its {@link Delivery} policy, so the question "may this email be lost?" is
 * answered once, next to the words, rather than re-decided by each caller.
 *
 * <p>A template declares its content once and {@link EmailLayout} renders it twice, as HTML and as
 * plain text - the two parts of a message cannot drift apart because neither is written by hand.
 *
 * <p>Holds the frontend base URL because the linking templates point into it. Pure functions —
 * nothing here touches the mail provider; {@link EmailService} does the sending.
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
     * The email-verification message: a button to the frontend verify page (prefilled with the
     * email and code) plus the raw code as a manual-entry fallback. The link only lands on the
     * page; verification itself happens when the recipient clicks there, so a mail scanner
     * prefetching the link can't spend the single-use code.
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
        return compose("TasKlean - Verify your email", delivery,
                heading("Verify your email"),
                paragraph("Verify your email address to finish setting up your account."),
                button("Verify email", link),
                paragraph("Or enter this code manually:"),
                code(code),
                note("This code expires in 5 minutes."));
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
        return compose("TasKlean - Account already exists", Delivery.ESSENTIAL,
                heading("Account already exists"),
                paragraph("Someone tried to create a TasKlean account with this email, but an "
                        + "account already exists."),
                paragraph("If this was you, just sign in instead - you can use your password or "
                        + "Google."),
                note("If it wasn't you, you can safely ignore this email."));
    }

    /**
     * A password-reset button to the frontend reset page, carrying the single-use token.
     *
     * <p>Advisory even though the link is the whole point: forgot-password answers an unknown
     * address with a silent no-op, so failing here would reveal which addresses exist.
     *
     * @param token the raw reset token (only ever sent here, never stored in plaintext)
     * @return the composed message
     */
    public EmailContent passwordReset(String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        return compose("TasKlean - Reset your password", Delivery.ADVISORY,
                heading("Reset your password"),
                paragraph("You asked to reset your TasKlean password. Use the link below to "
                        + "choose a new one."),
                button("Reset password", link),
                note("This link expires in 15 minutes and can be used once."),
                note("If you didn't request this, you can safely ignore this email - your password "
                        + "stays unchanged."));
    }

    /**
     * Tells a Google-only account holder that there's no password to reset (sent in place of a
     * reset link so the API response can stay neutral — anti-enumeration).
     *
     * @return the composed message
     */
    public EmailContent googleOnlyReset() {
        return compose("TasKlean - Password reset", Delivery.ADVISORY,
                heading("Password reset"),
                paragraph("You asked to reset your TasKlean password, but this account signs in "
                        + "with Google."),
                paragraph("There's no password to reset - just use \"Continue with Google\" to "
                        + "sign in."),
                note("If you didn't request this, you can safely ignore this email."));
    }

    /**
     * Confirms to the account owner that their password was just changed, so an unauthorized
     * reset is noticeable.
     *
     * @return the composed message
     */
    public EmailContent passwordChanged() {
        return compose("TasKlean - Your password was changed", Delivery.ADVISORY,
                heading("Your password was changed"),
                paragraph("Your TasKlean password was just changed, and you've been signed out on "
                        + "all devices."),
                note("If this was you, you can ignore this email."),
                note("If it wasn't you, reset your password immediately and contact support - your "
                        + "account may be compromised."));
    }

    /**
     * Confirms to a (previously Google-only) account holder that a password was just added, so an
     * unauthorized change is noticeable.
     *
     * @return the composed message
     */
    public EmailContent passwordSet() {
        return compose("TasKlean - A password was added to your account", Delivery.ADVISORY,
                heading("A password was added to your account"),
                paragraph("A password was just added to your TasKlean account. You can now sign in "
                        + "with your email and password, or keep using Google. For your security, "
                        + "your other sessions have been signed out."),
                note("If this was you, you can ignore this email."),
                note("If it wasn't you, reset your password immediately and contact support - your "
                        + "account may be compromised."));
    }

    /**
     * Confirms that a Google account was just linked, so an unauthorized link is noticeable.
     *
     * @return the composed message
     */
    public EmailContent googleLinked() {
        return compose("TasKlean - Google was linked to your account", Delivery.ADVISORY,
                heading("Google was linked to your account"),
                paragraph("A Google account was just linked to your TasKlean account, so you can "
                        + "now sign in with Google."),
                note("If this was you, you can ignore this email."),
                note("If it wasn't you, unlink Google and change your password from your account "
                        + "settings - your account may be compromised."));
    }

    /**
     * Confirms that a Google account was just unlinked, so an unauthorized change is noticeable.
     *
     * @return the composed message
     */
    public EmailContent googleUnlinked() {
        return compose("TasKlean - Google was unlinked from your account", Delivery.ADVISORY,
                heading("Google was unlinked from your account"),
                paragraph("Google sign-in was just removed from your TasKlean account. You'll now "
                        + "sign in with your email and password."),
                note("If this was you, you can ignore this email."),
                note("If it wasn't you, change your password immediately and contact support - "
                        + "your account may be compromised."));
    }

    /**
     * Confirms that the account was just deactivated, so an unauthorized deactivation is noticeable.
     *
     * @return the composed message
     */
    public EmailContent accountDeactivated() {
        return compose("TasKlean - Your account was deactivated", Delivery.ADVISORY,
                heading("Your account was deactivated"),
                paragraph("Your TasKlean account was just deactivated and you've been signed out "
                        + "everywhere."),
                note("If this was you, no further action is needed."),
                note("If it wasn't you, contact support right away - your account may be "
                        + "compromised."));
    }

    /**
     * Confirms that two-factor authentication was just turned on, so an unauthorized change is
     * noticeable. Never carries the secret or any recovery code.
     *
     * @return the composed message
     */
    public EmailContent twoFactorEnabled() {
        return compose("TasKlean - Two-factor authentication is on", Delivery.ADVISORY,
                heading("Two-factor authentication is on"),
                paragraph("Two-factor authentication was just turned on for your TasKlean account. "
                        + "From now on you'll enter a code from your authenticator app when you "
                        + "sign in, and your other sessions have been signed out."),
                paragraph("Keep your recovery codes somewhere safe - they're the only way back in "
                        + "if you lose your phone."),
                note("If this wasn't you, contact support right away - your account may be "
                        + "compromised."));
    }

    /**
     * Confirms that two-factor authentication was just turned off, so an unauthorized change is
     * noticeable.
     *
     * @return the composed message
     */
    public EmailContent twoFactorDisabled() {
        return compose("TasKlean - Two-factor authentication is off", Delivery.ADVISORY,
                heading("Two-factor authentication is off"),
                paragraph("Two-factor authentication was just turned off for your TasKlean "
                        + "account, and your other sessions have been signed out. You'll now sign "
                        + "in with your password alone."),
                note("If this wasn't you, turn it back on and change your password immediately - "
                        + "your account may be compromised."));
    }

    /**
     * Confirms that a fresh set of recovery codes was issued, which invalidated the previous set.
     *
     * @return the composed message
     */
    public EmailContent recoveryCodesRegenerated() {
        return compose("TasKlean - New recovery codes were generated", Delivery.ADVISORY,
                heading("New recovery codes were generated"),
                paragraph("A new set of two-factor recovery codes was just generated for your "
                        + "TasKlean account. Your previous codes no longer work."),
                note("If this wasn't you, contact support right away - your account may be "
                        + "compromised."));
    }

    /**
     * Alerts the account owner that a recovery code was just used to sign in, which is the one
     * two-factor path that needs no phone - so an unrecognised use must be noticeable.
     *
     * @return the composed message
     */
    public EmailContent recoveryCodeUsed() {
        return compose("TasKlean - A recovery code was used to sign in", Delivery.ADVISORY,
                heading("A recovery code was used to sign in"),
                paragraph("Someone signed in to your TasKlean account with a two-factor recovery "
                        + "code instead of your authenticator app. That code is now used up."),
                note("If this was you, no further action is needed - you can generate a fresh set "
                        + "of codes in your account settings."),
                note("If it wasn't you, change your password and generate new recovery codes right "
                        + "away - your account may be compromised."));
    }

    /**
     * Alerts the account owner that their account was signed into from a new device, so an
     * unrecognised sign-in is noticeable. Carries only the device label and IP, never credentials.
     *
     * @param deviceName the friendly device label (e.g. "Chrome on Windows")
     * @param ipAddress  the IP the sign-in came from; the row is omitted when null/blank
     * @return the composed message
     */
    public EmailContent newSignIn(String deviceName, String ipAddress) {
        List<EmailBlock> blocks = new ArrayList<>(List.of(
                heading("New sign-in to your account"),
                paragraph("Your TasKlean account was just signed into from a new device:"),
                dataRow("Device", deviceName)));
        if (ipAddress != null && !ipAddress.isBlank()) {
            blocks.add(dataRow("IP address", ipAddress));
        }
        blocks.add(note("If this was you, you can ignore this email."));
        blocks.add(note("If it wasn't you, change your password and sign out everywhere from your "
                + "account settings."));
        return compose("TasKlean - New sign-in to your account", Delivery.ADVISORY, blocks);
    }

    private EmailContent compose(String subject, Delivery delivery, EmailBlock... blocks) {
        return compose(subject, delivery, List.of(blocks));
    }

    private EmailContent compose(String subject, Delivery delivery, List<EmailBlock> blocks) {
        return new EmailContent(subject, EmailLayout.renderText(blocks),
                EmailLayout.renderHtml(blocks), delivery);
    }
}
