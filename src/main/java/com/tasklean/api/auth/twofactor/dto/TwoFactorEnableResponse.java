package com.tasklean.api.auth.twofactor.dto;

import java.util.List;

/**
 * The result of turning two-factor on: the one-time recovery codes, plus a fresh session for the
 * current device because enabling signs every other device out.
 *
 * @param recoveryCodes the single-use codes, shown to the user only here
 * @param token         the new access token for the current device
 * @param refreshToken  the new refresh token for the current device
 */
public record TwoFactorEnableResponse(List<String> recoveryCodes, String token, String refreshToken) {
}
