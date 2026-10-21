-- Repurpose the device table as the durable per-device record behind sign-in sessions.
-- Push registration is now optional, so the push token may be null; a stable, client-generated
-- id (sent as the X-Device-Id header) identifies a device across logins and token rotation.
ALTER TABLE device ALTER COLUMN device_token DROP NOT NULL;
ALTER TABLE device ADD COLUMN client_device_id VARCHAR(255);
ALTER TABLE device ADD COLUMN ip_address VARCHAR(45);
ALTER TABLE device ADD COLUMN user_agent VARCHAR(512);

-- One device row per (user, client device id). Postgres allows multiple NULLs here, so
-- push-only and legacy rows (no client id) don't collide.
CREATE UNIQUE INDEX uq_device_user_client ON device (user_id, client_device_id);

-- Link each refresh token to the device it was issued on. Nullable: legacy tokens and any login
-- without a device id have none. Rotation reissues on the same device, so the device row is the
-- stable session identity even though the token value changes.
ALTER TABLE refresh_token ADD COLUMN device_id BIGINT;
ALTER TABLE refresh_token ADD CONSTRAINT fk_refresh_token_device
    FOREIGN KEY (device_id) REFERENCES device (id_device) ON DELETE CASCADE;
CREATE INDEX idx_refresh_token_device_id ON refresh_token (device_id);
