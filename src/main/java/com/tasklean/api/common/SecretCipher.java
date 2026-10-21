package com.tasklean.api.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts secrets that have to be recoverable rather than merely comparable — today the TOTP
 * secret, which must be decrypted to verify a code, so {@link SecureTokens} hashing cannot be
 * used. AES-256-GCM, so stored values are both confidential and tamper-evident.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /**
     * @param encodedKey Base64-encoded 32-byte key from {@code TWO_FACTOR_ENC_KEY}
     * @throws IllegalStateException if the key is missing, not Base64, or not 32 bytes — a
     *                               misconfigured key must fail at startup, not at first use
     */
    public SecretCipher(@Value("${twofactor.encryption-key}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TWO_FACTOR_ENC_KEY is not valid Base64");
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "TWO_FACTOR_ENC_KEY must decode to " + KEY_LENGTH_BYTES + " bytes (AES-256), got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts a secret for storage.
     *
     * @param plaintext the value to protect
     * @return Base64 of the random IV followed by the ciphertext and GCM tag
     * @throws IllegalStateException if encryption fails
     */
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        RANDOM.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV is prefixed rather than stored separately: it is not secret, only single-use.
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    /**
     * Decrypts a stored secret.
     *
     * @param stored the value produced by {@link #encrypt(String)}
     * @return the original secret
     * @throws IllegalStateException if the value is malformed, truncated, or fails its GCM tag —
     *                               i.e. it was tampered with or encrypted under another key
     */
    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("Stored secret is too short to be valid");
            }

            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Never echo the stored value or the cause's message - both can carry key material.
            throw new IllegalStateException("Could not decrypt secret");
        }
    }
}
