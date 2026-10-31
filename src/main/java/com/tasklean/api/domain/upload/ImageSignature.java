package com.tasklean.api.domain.upload;

/**
 * Checks that an object's leading bytes are the format its content type claims. A content type is
 * only ever the uploader's assertion — the bucket stores whatever header it was given — so this is
 * what stops arbitrary bytes being attached as an image.
 */
final class ImageSignature {

    /** Enough for the longest signature checked here (WebP needs 12). */
    static final int BYTES_NEEDED = 12;

    private ImageSignature() {
    }

    /**
     * Whether the given leading bytes are the declared type.
     *
     * @param contentType the declared type; one of the allowlisted image types
     * @param prefix      the object's leading bytes
     * @return {@code true} if the bytes start with that format's signature
     */
    static boolean matches(String contentType, byte[] prefix) {
        if (prefix == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> startsWith(prefix, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(prefix, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            // RIFF container: "RIFF", a four-byte length we do not care about, then "WEBP".
            case "image/webp" -> startsWith(prefix, 0x52, 0x49, 0x46, 0x46)
                    && prefix.length >= BYTES_NEEDED
                    && prefix[8] == 0x57 && prefix[9] == 0x45 && prefix[10] == 0x42 && prefix[11] == 0x50;
            default -> false;
        };
    }

    private static boolean startsWith(byte[] prefix, int... signature) {
        if (prefix.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((prefix[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
