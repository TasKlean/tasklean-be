CREATE TABLE tag (
                     id_tag BIGSERIAL PRIMARY KEY,
                     name VARCHAR(100) NOT NULL,
                     color VARCHAR(7),
                     is_active BOOLEAN DEFAULT TRUE NOT NULL,
                     date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                     date_updated TIMESTAMP DEFAULT NOW(),
                     group_id BIGINT NOT NULL,
                     CONSTRAINT fk_tag_group FOREIGN KEY (group_id) REFERENCES "group" (id_group),
                     CONSTRAINT unique_tag_per_group UNIQUE (group_id, name)
);

CREATE INDEX idx_tag_group_id ON tag (group_id);