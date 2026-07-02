CREATE TABLE task_tag (
                          id_task_tag BIGSERIAL PRIMARY KEY,
                          date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                          task_id BIGINT NOT NULL,
                          tag_id BIGINT NOT NULL,
                          CONSTRAINT fk_tt_task FOREIGN KEY (task_id) REFERENCES task (id_task) ON DELETE CASCADE,
                          CONSTRAINT fk_tt_tag FOREIGN KEY (tag_id) REFERENCES tag (id_tag) ON DELETE CASCADE,
                          CONSTRAINT unique_task_tag UNIQUE (task_id, tag_id)
);

CREATE INDEX idx_tt_task_id ON task_tag (task_id);
CREATE INDEX idx_tt_tag_id ON task_tag (tag_id);