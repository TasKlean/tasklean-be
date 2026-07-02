CREATE TABLE task (
                      id_task BIGSERIAL PRIMARY KEY,
                      uid VARCHAR(500) UNIQUE NOT NULL,
                      name VARCHAR(255) NOT NULL,
                      description TEXT,
                      task_photo_url VARCHAR(500),
                      priority VARCHAR(50) NOT NULL CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
                      recurrence_type VARCHAR(50) CHECK (recurrence_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM')),
                      recurrence_pattern JSONB,
                      next_due_date TIMESTAMP,
                      requires_photo_proof BOOLEAN DEFAULT FALSE NOT NULL,
                      estimated_time_minutes INTEGER,
                      status VARCHAR(50) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED')),
                      is_active BOOLEAN DEFAULT TRUE NOT NULL,
                      date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                      date_updated TIMESTAMP DEFAULT NOW(),
                      date_completed TIMESTAMP,
                      group_id BIGINT NOT NULL,
                      created_by BIGINT NOT NULL,
                      assigned_to BIGINT,
                      category_id BIGINT,
                      CONSTRAINT fk_task_group FOREIGN KEY (group_id) REFERENCES "group" (id_group),
                      CONSTRAINT fk_task_created_by FOREIGN KEY (created_by) REFERENCES group_member (id_group_member),
                      CONSTRAINT fk_task_assigned_to FOREIGN KEY (assigned_to) REFERENCES group_member (id_group_member),
                      CONSTRAINT fk_task_category FOREIGN KEY (category_id) REFERENCES category (id_category)
);

CREATE INDEX idx_task_group_id ON task (group_id);
CREATE INDEX idx_task_assigned_to ON task (assigned_to);
CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_priority ON task (priority);
CREATE INDEX idx_task_uid ON task (uid);