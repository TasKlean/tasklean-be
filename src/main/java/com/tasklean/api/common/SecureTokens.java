package com.tasklean.api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Helpers for opaque bearer tokens (refresh tokens, password-reset tokens): a cryptographically
 * random value for the caller, and a SHA-256 hash so only the hash is ever persisted or compared.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /**
     * Generates a 256-bit URL-safe random token.
     *
     * @return the raw (unhashed) token to hand to the caller
     */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hashes a raw token for storage/lookup, so the plaintext is never persisted.
     *
     * @param rawToken the raw token
     * @return the Base64 URL-encoded hash
     */
    public static String sha256(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK, so this never happens at runtime.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
