-- Two-factor authentication: the enrolled factor, its single-use recovery codes, and the
-- short-lived challenges that sit between a correct password and an issued token.

-- One factor per user, created on setup and only trusted once confirmed with a real code.
CREATE TABLE user_two_factor (
    id_user_two_factor BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES "user"(id_user) ON DELETE CASCADE,
    -- Which backend owns the factor (see TwoFactorProvider). LOCAL keeps the secret here;
    -- a hosted provider would keep factor_ref instead and leave the secret null.
    provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    factor_ref VARCHAR(255),
    secret_encrypted TEXT,
    -- Recorded per factor so a future move to SHA-256 needs no migration: existing rows keep
    -- verifying with SHA-1 while new enrollments can use something else.
    digest VARCHAR(10) NOT NULL DEFAULT 'SHA1',
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMP,
    -- Highest TOTP time step already accepted, so the same code cannot be replayed inside the
    -- skew window.
    last_used_step BIGINT,
    date_created TIMESTAMP NOT NULL DEFAULT NOW(),
    date_updated TIMESTAMP DEFAULT NOW()
);

-- No separate index on user_id: the UNIQUE constraint already backs every lookup by user.

-- Backup codes, hashed like any other credential and consumed exactly once.
CREATE TABLE two_factor_recovery_code (
    id_two_factor_recovery_code BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id_user) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMP,
    date_created TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Unique per user rather than globally: two users colliding is not an error, a user holding
    -- the same code twice is. Leftmost-prefixed on user_id, so it also backs the per-user list.
    CONSTRAINT uq_two_factor_recovery_code_user_hash UNIQUE (user_id, code_hash)
);

-- A pending sign-in: the password (or Google token) was accepted, the second factor was not yet.
-- The token is opaque and stored hashed, never a JWT - a JWT signed with our key would be
-- accepted by JwtAuthenticationFilter as a full access token.
CREATE TABLE two_factor_challenge (
    id_two_factor_challenge BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id_user) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    -- Capped per challenge, so rotating IP addresses does not buy an attacker more guesses.
    attempts INT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP,
    date_created TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_two_factor_challenge_user_id ON two_factor_challenge(user_id);
CREATE INDEX idx_two_factor_challenge_expires_at ON two_factor_challenge(expires_at);

-- Deny-all through the API's anon path (owner connection bypasses it); no policies.
ALTER TABLE user_two_factor ENABLE ROW LEVEL SECURITY;
ALTER TABLE two_factor_recovery_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE two_factor_challenge ENABLE ROW LEVEL SECURITY;
