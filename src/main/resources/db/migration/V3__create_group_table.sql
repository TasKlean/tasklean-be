CREATE TABLE "group" (
                         id_group BIGSERIAL PRIMARY KEY,
                         uid VARCHAR(500) UNIQUE NOT NULL,
                         name VARCHAR(255) NOT NULL,
                         description TEXT,
                         photo_url VARCHAR(500),
                         invite_code VARCHAR(50) UNIQUE NOT NULL,
                         is_active BOOLEAN DEFAULT TRUE NOT NULL,
                         date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                         date_updated TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_group_uid ON "group" (uid);
CREATE INDEX idx_group_invite_code ON "group" (invite_code);