-- A task_completion row stops being only a completion and becomes the record of one scheduled
-- instance of a task and what became of it: done, skipped, or missed. Recurrence is handled by
-- rolling task.next_due_date forward and writing one row per instance that happened, rather than
-- materialising a calendar of future rows - the whole cost of that sat in rows nothing reads, and
-- no part of the feature needs to see past the next due date. The table keeps its name.
ALTER TABLE task_completion
    ADD COLUMN due_date TIMESTAMP,
    ADD COLUMN outcome VARCHAR(50) DEFAULT 'DONE' NOT NULL,
    ADD COLUMN is_active BOOLEAN DEFAULT TRUE NOT NULL,
    ADD COLUMN minutes INTEGER;

ALTER TABLE task_completion
    ADD CONSTRAINT chk_tc_outcome CHECK (outcome IN ('DONE', 'SKIPPED', 'MISSED'));

-- Existing rows are all completions, which is what the default already made them.

-- The column widens from "who completed this" to "who this instance is attributed to", so a miss
-- names whoever was on the hook - and an unassigned task leaves nobody to name.
ALTER TABLE task_completion
    ALTER COLUMN group_member_id DROP NOT NULL;

-- One row per instance. This is what stops the same due date being recorded twice, whether by two
-- people completing at once or by the sweep racing a completion. Partial, so undoing a row leaves
-- its date free to be recorded again. A one-off task has no due date, and Postgres treats nulls as
-- distinct, so those are guarded by task.status instead.
CREATE UNIQUE INDEX uq_tc_task_due ON task_completion (task_id, due_date) WHERE is_active;

-- The per-member share reads a window of one member's rows, so the date belongs in the index with
-- them. It replaces idx_tc_member_id, whose every use is served by this one's leading column.
CREATE INDEX idx_tc_member_completed ON task_completion (group_member_id, date_completed);
DROP INDEX idx_tc_member_id;

-- Photo proof is now an upload of ours, addressed by the upload's uid, as a group's picture (V25)
-- and a user's (V31) already are. Existing values are discarded: they are external links, which is
-- what is being dropped. The same goes for a task's own reference picture.
ALTER TABLE task_completion DROP COLUMN completion_photo_url;
ALTER TABLE task_completion ADD COLUMN completion_photo_uid VARCHAR(100);
ALTER TABLE task_completion
    ADD CONSTRAINT fk_tc_photo_upload FOREIGN KEY (completion_photo_uid) REFERENCES upload (uid);

ALTER TABLE task DROP COLUMN task_photo_url;
ALTER TABLE task ADD COLUMN photo_uid VARCHAR(100);
ALTER TABLE task
    ADD CONSTRAINT fk_task_photo_upload FOREIGN KEY (photo_uid) REFERENCES upload (uid);

-- The board asks one question - what is open in this household, and when is it due - and the sweep
-- asks the same one without the group. idx_task_status stays for the second shape.
CREATE INDEX idx_task_group_status_due ON task (group_id, status, next_due_date);
