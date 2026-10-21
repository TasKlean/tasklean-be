package com.tasklean.api.auth.twofactor.provider;

/**
 * The outcome of checking a submitted code.
 *
 * @param valid    whether the code matched
 * @param timeStep the absolute time step the code belongs to, so the caller can reject a replay of
 *                 a code already accepted inside its skew window. {@code null} when the provider
 *                 enforces single use itself, and always {@code null} on failure
 */
public record TwoFactorVerification(boolean valid, Long timeStep) {

    public static TwoFactorVerification invalid() {
        return new TwoFactorVerification(false, null);
    }

    public static TwoFactorVerification validAt(long timeStep) {
        return new TwoFactorVerification(true, timeStep);
    }
}
