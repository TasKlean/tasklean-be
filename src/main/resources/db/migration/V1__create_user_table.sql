CREATE TABLE "user" (
                        id_user BIGSERIAL PRIMARY KEY,
                        uid VARCHAR(100) UNIQUE NOT NULL,
                        email VARCHAR(255) UNIQUE NOT NULL,
                        password_hash VARCHAR(255),
                        name VARCHAR(100) NOT NULL,
                        middle_name VARCHAR(100),
                        last_name VARCHAR(100) NOT NULL,
                        photo_url VARCHAR(500),
                        google_sub VARCHAR(255) UNIQUE,
                        is_active BOOLEAN DEFAULT TRUE NOT NULL,
                        date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                        date_updated TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_email ON "user" (email);
CREATE INDEX idx_user_uid ON "user" (uid);
CREATE INDEX idx_user_google_sub ON "user" (google_sub);