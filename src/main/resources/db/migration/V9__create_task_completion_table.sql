CREATE TABLE task_completion (
                                 id_task_completion BIGSERIAL PRIMARY KEY,
                                 completion_photo_url VARCHAR(500),
                                 completion_note TEXT,
                                 date_completed TIMESTAMP DEFAULT NOW() NOT NULL,
                                 date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                                 task_id BIGINT NOT NULL,
                                 group_member_id BIGINT NOT NULL,
                                 CONSTRAINT fk_tc_task FOREIGN KEY (task_id) REFERENCES task (id_task),
                                 CONSTRAINT fk_tc_member FOREIGN KEY (group_member_id) REFERENCES group_member (id_group_member)
);

CREATE INDEX idx_tc_task_id ON task_completion (task_id);
CREATE INDEX idx_tc_member_id ON task_completion (group_member_id);