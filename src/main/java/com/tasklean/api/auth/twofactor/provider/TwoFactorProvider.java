package com.tasklean.api.auth.twofactor.provider;

/**
 * The seam between our two-factor flows and whatever actually owns the factor. Everything above it
 * — enrollment, sign-in challenges, recovery codes, audit, email — is provider-agnostic, so a
 * hosted factor service can replace the local implementation without touching those flows.
 */
public interface TwoFactorProvider {

    /**
     * Identifies this backend, for the {@code provider} column on the stored factor.
     *
     * @return the provider type
     */
    TwoFactorProviderType type();

    /**
     * Creates a new, unconfirmed factor. Nothing is persisted here: the caller stores the returned
     * credential and only marks the factor enabled once the user proves they can produce a code.
     *
     * @param accountEmail the account the authenticator app labels the entry with
     * @return the credential to persist plus the {@code otpauth://} URI for the client
     */
    FactorEnrollment enroll(String accountEmail);

    /**
     * Checks a code submitted by the user. Never throws for bad input — a malformed code or
     * credential is simply not valid.
     *
     * @param credential the stored credential (decrypted, for a local factor)
     * @param code       the code the user submitted
     * @return the outcome, carrying the matched time step when the caller must guard against replay
     */
    TwoFactorVerification verify(String credential, String code);
}
