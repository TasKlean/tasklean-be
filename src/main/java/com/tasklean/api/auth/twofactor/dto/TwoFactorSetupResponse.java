package com.tasklean.api.auth.twofactor.dto;

/**
 * What the client needs to enroll an authenticator app. The factor is not active yet - it becomes
 * active only once a code from it is confirmed.
 *
 * @param secret     the Base32 secret, for manual entry when a QR code can't be scanned
 * @param otpauthUri the {@code otpauth://totp/...} URI to render as a QR code
 */
public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
