package com.tasklean.api.auth.twofactor.provider;

/**
 * The result of starting an enrollment: what the backend needs us to persist, and the URI the
 * user's authenticator app consumes.
 *
 * @param credential what identifies the factor to its provider — for {@link TwoFactorProviderType#LOCAL}
 *                   the Base32 TOTP secret, which the caller must encrypt before persisting; for a
 *                   hosted provider an opaque factor reference
 * @param otpauthUri the {@code otpauth://totp/...} URI the client renders as a QR code
 */
public record FactorEnrollment(String credential, String otpauthUri) {
}
