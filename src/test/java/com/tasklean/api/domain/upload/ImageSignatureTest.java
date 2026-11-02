package com.tasklean.api.domain.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignatureTest {

    private static byte[] padded(int... leading) {
        byte[] prefix = new byte[ImageSignature.BYTES_NEEDED];
        for (int i = 0; i < leading.length; i++) {
            prefix[i] = (byte) leading[i];
        }
        return prefix;
    }

    private static byte[] webp(int... tail) {
        byte[] prefix = padded(0x52, 0x49, 0x46, 0x46);
        for (int i = 0; i < tail.length; i++) {
            prefix[8 + i] = (byte) tail[i];
        }
        return prefix;
    }

    @Test
    void matches_jpegBytesDeclaredAsJpeg_isTrue() {
        assertThat(ImageSignature.matches("image/jpeg", padded(0xFF, 0xD8, 0xFF))).isTrue();
    }

    @Test
    void matches_pngBytesDeclaredAsPng_isTrue() {
        assertThat(ImageSignature.matches("image/png",
                padded(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isTrue();
    }

    @Test
    void matches_webpBytesDeclaredAsWebp_isTrue() {
        // RIFF, four bytes of length we ignore, then WEBP.
        assertThat(ImageSignature.matches("image/webp", webp(0x57, 0x45, 0x42, 0x50))).isTrue();
    }

    @Test
    void matches_riffThatIsNotWebp_isFalse() {
        // A WAV file is also RIFF, so the container alone proves nothing.
        assertThat(ImageSignature.matches("image/webp", webp(0x57, 0x41, 0x56, 0x45))).isFalse();
    }

    @Test
    void matches_pngBytesDeclaredAsJpeg_isFalse() {
        assertThat(ImageSignature.matches("image/jpeg",
                padded(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isFalse();
    }

    @Test
    void matches_scriptBytesDeclaredAsPng_isFalse() {
        // The case the check exists for: the bucket stored whatever header it was handed.
        assertThat(ImageSignature.matches("image/png", "<?php echo 1;".getBytes())).isFalse();
    }

    @Test
    void matches_typeOutsideTheAllowlist_isFalse() {
        assertThat(ImageSignature.matches("image/svg+xml", padded(0x3C, 0x73, 0x76, 0x67))).isFalse();
    }

    @Test
    void matches_noBytesRead_isFalse() {
        assertThat(ImageSignature.matches("image/png", null)).isFalse();
    }

    @Test
    void matches_fewerBytesThanTheSignature_isFalse() {
        // A truncated object must not pass on the bytes that happen to be there.
        assertThat(ImageSignature.matches("image/png", new byte[]{(byte) 0x89, 0x50})).isFalse();
    }

    @Test
    void matches_webpTruncatedAfterRiff_isFalse() {
        assertThat(ImageSignature.matches("image/webp", new byte[]{0x52, 0x49, 0x46, 0x46})).isFalse();
    }
}
