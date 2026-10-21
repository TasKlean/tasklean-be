package com.tasklean.api.auth.twofactor.provider;

/**
 * Which backend owns a user's second factor. Persisted on the factor row so a later migration to
 * a hosted factor service can run alongside existing local enrollments.
 */
public enum TwoFactorProviderType {

    /** TOTP verified in-process against a secret we store encrypted. */
    LOCAL
}
