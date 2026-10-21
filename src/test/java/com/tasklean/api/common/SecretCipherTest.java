package com.tasklean.api.common;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the secret-at-rest cipher: values round-trip, two encryptions of the same secret differ,
 * and anything tampered with or encrypted under another key is rejected rather than half-decrypted.
 */
class SecretCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("tasklean-test-key-exactly-32byte".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("another-test-key-exactly-32bytes".getBytes());

    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void encryptThenDecrypt_returnsTheOriginalSecret() {
        assertThat(cipher.decrypt(cipher.encrypt(SECRET))).isEqualTo(SECRET);
    }

    @Test
    void encrypt_sameSecretTwice_producesDifferentCiphertexts() {
        // A fresh IV per encryption, so equal secrets are not recognisable as equal in the table.
        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(cipher.encrypt(SECRET));
    }

    @Test
    void encrypt_output_doesNotContainThePlaintext() {
        assertThat(cipher.encrypt(SECRET)).doesNotContain(SECRET);
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(SECRET));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_valueEncryptedUnderAnotherKey_throws() {
        String foreign = new SecretCipher(OTHER_KEY).encrypt(SECRET);

        assertThatThrownBy(() -> cipher.decrypt(foreign))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_malformedValue_throwsWithoutEchoingIt() {
        assertThatThrownBy(() -> cipher.decrypt("not base64 at all!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not decrypt secret");
    }

    @Test
    void decrypt_valueShorterThanTheIv_throws() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[8]);

        assertThatThrownBy(() -> cipher.decrypt(tooShort))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- fail fast on a misconfigured key, at startup rather than at first enrollment ---

    @Test
    void construct_keyOfTheWrongLength_throws() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new SecretCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void construct_keyThatIsNotBase64_throws() {
        assertThatThrownBy(() -> new SecretCipher("nope!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
