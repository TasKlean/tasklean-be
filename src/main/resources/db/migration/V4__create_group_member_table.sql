CREATE TABLE group_member (
                              id_group_member BIGSERIAL PRIMARY KEY,
                              role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
                              is_active BOOLEAN DEFAULT TRUE NOT NULL,
                              date_joined TIMESTAMP DEFAULT NOW() NOT NULL,
                              date_left TIMESTAMP,
                              added_by BIGINT,
                              removed_by BIGINT,
                              user_id BIGINT NOT NULL,
                              group_id BIGINT NOT NULL,
                              CONSTRAINT fk_gm_user FOREIGN KEY (user_id) REFERENCES "user" (id_user),
                              CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES "group" (id_group),
                              CONSTRAINT fk_gm_added_by FOREIGN KEY (added_by) REFERENCES group_member (id_group_member),
                              CONSTRAINT fk_gm_removed_by FOREIGN KEY (removed_by) REFERENCES group_member (id_group_member),
                              CONSTRAINT unique_group_member UNIQUE (user_id, group_id)
);

CREATE INDEX idx_gm_user_id ON group_member (user_id);
CREATE INDEX idx_gm_group_id ON group_member (group_id);