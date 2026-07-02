CREATE TABLE audit_log (
                           id_audit_log BIGSERIAL PRIMARY KEY,
                           entity_type VARCHAR(50) NOT NULL,
                           entity_id BIGINT NOT NULL,
                           action VARCHAR(50) NOT NULL,
                           action_message TEXT NOT NULL,
                           ip_address VARCHAR(45),
                           date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                           group_member_id BIGINT,
                           group_id BIGINT,
                           CONSTRAINT fk_al_member FOREIGN KEY (group_member_id) REFERENCES group_member (id_group_member),
                           CONSTRAINT fk_al_group FOREIGN KEY (group_id) REFERENCES "group" (id_group)
);

CREATE INDEX idx_al_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_al_member_id ON audit_log (group_member_id);
CREATE INDEX idx_al_group_id ON audit_log (group_id);
CREATE INDEX idx_al_date ON audit_log (date_created);