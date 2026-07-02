CREATE TABLE notification (
                              id_notification BIGSERIAL PRIMARY KEY,
                              type VARCHAR(50) NOT NULL,
                              title VARCHAR(255) NOT NULL,
                              message TEXT NOT NULL,
                              is_read BOOLEAN DEFAULT FALSE NOT NULL,
                              read_at TIMESTAMP,
                              date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                              user_id BIGINT NOT NULL,
                              created_by BIGINT,
                              task_id BIGINT,
                              group_id BIGINT,
                              CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES "user" (id_user),
                              CONSTRAINT fk_notif_created_by FOREIGN KEY (created_by) REFERENCES "user" (id_user),
                              CONSTRAINT fk_notif_task FOREIGN KEY (task_id) REFERENCES task (id_task),
                              CONSTRAINT fk_notif_group FOREIGN KEY (group_id) REFERENCES "group" (id_group)
);

CREATE INDEX idx_notif_user_id ON notification (user_id);
CREATE INDEX idx_notif_is_read ON notification (user_id, is_read);
CREATE INDEX idx_notif_group_id ON notification (group_id);