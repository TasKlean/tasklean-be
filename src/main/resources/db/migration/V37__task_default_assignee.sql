-- Whose chore this normally is, beside assigned_to, which is whose round it is right now.
-- A round ending hands assigned_to back to this, so an owner survives every repeat.
ALTER TABLE task
    ADD COLUMN default_assignee BIGINT,
    ADD CONSTRAINT fk_task_default_assignee FOREIGN KEY (default_assignee) REFERENCES group_member (id_group_member);

CREATE INDEX idx_task_default_assignee ON task (default_assignee);
