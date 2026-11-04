package com.tasklean.api.common.validation;

/**
 * The maximum lengths request DTOs bound their string fields to, plus the few that bound what an
 * upstream provider hands us. Centralized because most are a column width or a protocol limit that
 * more than one caller shares, and because an unbounded field fails in Postgres as a 500 rather
 * than in validation as a 400.
 */
public final class FieldLimits {

    /** RFC 5321's limit, and what the frontend's own email rule uses; the column holds 255. */
    public static final int EMAIL = 254;

    /** The name, middle_name and last_name columns on "user". */
    public static final int NAME = 100;

    /** The photo_url column on "user", holding a profile picture URL from Google. */
    public static final int PHOTO_URL = 500;

    /** The password policy's own maximum - {@link PasswordPolicyValidator} reads it from here. */
    public static final int PASSWORD = 64;

    /**
     * For a password that is only checked against a stored hash, never stored. Deliberately looser
     * than {@link #PASSWORD}: re-authentication must not reject a password that predates the
     * policy, and BCrypt reads no more than 72 bytes of it anyway.
     */
    public static final int PASSWORD_INPUT = 256;

    /** A 6-digit authenticator code or a 9-character recovery code, with room for typed spacing. */
    public static final int CODE = 32;

    /** Our opaque tokens are a 43-character Base64 URL encoding of 32 random bytes. */
    public static final int TOKEN = 64;

    /** A public entity identifier - a UUID string. The narrowest uid column ("user") holds 100. */
    public static final int UID = 100;

    /** The name column on "group". */
    public static final int GROUP_NAME = 255;

    /** A free-text description. The column is TEXT, so this bound is ours, not Postgres's. */
    public static final int DESCRIPTION = 2000;

    /**
     * A search string a list endpoint narrows itself by. Nothing is stored, so the bound is only to
     * refuse a paste: longer than this is nobody's search. Mirrors the frontend's own
     * {@code QUERY_MAX_LENGTH} - the frontend trims to it before sending, so a longer value
     * arriving means a hand-edited URL, and the 400 is the point.
     */
    public static final int SEARCH_TEXT = 100;

    /** A Google ID token is a JWT of roughly 1-2KB; enough headroom without inviting a flood. */
    public static final int GOOGLE_ID_TOKEN = 4096;

    /** An image MIME type; the allowlist on the field is what actually narrows it. */
    public static final int CONTENT_TYPE = 100;

    /**
     * The largest image we sign for, in bytes. One cap for every purpose rather than one each:
     * the point is to refuse something absurd, and a @Max on the request makes it a 400 before
     * a signature exists. Phone photos are 3-8MB, so the frontend downscales first.
     */
    public static final long IMAGE_BYTES = 5L * 1024 * 1024;

    /** The per-login nonce secret the frontend generates and we only hash. */
    public static final int NONCE = 256;

    private FieldLimits() {
    }
}
