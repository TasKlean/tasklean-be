-- Per-user UI settings (colour theme + language). One row per user, created lazily on the first
-- write: a read for a user with no row returns the defaults, so existing accounts need no backfill.
CREATE TABLE user_settings (
    id_user_settings BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES "user"(id_user) ON DELETE CASCADE,
    theme VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    locale VARCHAR(10) NOT NULL DEFAULT 'EN',
    date_created TIMESTAMP NOT NULL DEFAULT NOW(),
    date_updated TIMESTAMP DEFAULT NOW()
);

-- No separate index on user_id: the UNIQUE constraint already backs every lookup by user.
-- The column DEFAULTs mirror the entity's defaults and only act as a safety net for direct SQL.

-- Deny-all through the API's anon path (owner connection bypasses it); no policies.
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;
