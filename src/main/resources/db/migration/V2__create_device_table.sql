CREATE TABLE device (
                        id_device BIGSERIAL PRIMARY KEY,
                        device_token VARCHAR(500) UNIQUE NOT NULL,
                        device_type VARCHAR(50) NOT NULL,
                        device_name VARCHAR(255),
                        browser_info VARCHAR(255),
                        os_version VARCHAR(50),
                        app_version VARCHAR(50),
                        is_active BOOLEAN DEFAULT TRUE NOT NULL,
                        last_used TIMESTAMP DEFAULT NOW(),
                        date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                        date_updated TIMESTAMP DEFAULT NOW(),
                        user_id BIGINT NOT NULL,
                        CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES "user" (id_user) ON DELETE CASCADE
);

CREATE INDEX idx_device_user_id ON device (user_id);
CREATE INDEX idx_device_token ON device (device_token);