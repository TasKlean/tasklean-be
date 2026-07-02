CREATE TABLE category (
                          id_category BIGSERIAL PRIMARY KEY,
                          name VARCHAR(100) NOT NULL,
                          color VARCHAR(7),
                          icon VARCHAR(50),
                          is_active BOOLEAN DEFAULT TRUE NOT NULL,
                          date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                          date_updated TIMESTAMP DEFAULT NOW(),
                          group_id BIGINT NOT NULL,
                          CONSTRAINT fk_category_group FOREIGN KEY (group_id) REFERENCES "group" (id_group),
                          CONSTRAINT unique_category_per_group UNIQUE (group_id, name)
);

CREATE INDEX idx_category_group_id ON category (group_id);