-- Per-user, per-type notification delivery preferences. A row exists only once the user changes
-- something: a missing row means "enabled on every channel", so existing accounts need no backfill
-- and adding a notification type needs no migration (only a new enum value).
CREATE TABLE notification_settings (
    id_notification_settings BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id_user) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    in_app BOOLEAN NOT NULL DEFAULT TRUE,
    email BOOLEAN NOT NULL DEFAULT TRUE,
    date_created TIMESTAMP NOT NULL DEFAULT NOW(),
    date_updated TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_notification_settings_user_type UNIQUE (user_id, type)
);

-- No separate index on user_id: the unique (user_id, type) index is leftmost-prefixed on user_id,
-- so it already backs "all settings for this user" lookups.

-- Deny-all through the API's anon path (owner connection bypasses it); no policies.
ALTER TABLE notification_settings ENABLE ROW LEVEL SECURITY;
