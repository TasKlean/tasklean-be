package com.tasklean.api.auth;

/**
 * A verified Google identity: the stable subject id and the Google-verified email. Returned by
 * {@link GoogleOAuthService#verifyLinkToken} for account-linking decisions.
 *
 * @param subject the Google subject id (stable per-account identifier)
 * @param email   the Google-verified email address
 */
public record GoogleIdentity(String subject, String email) {
}
