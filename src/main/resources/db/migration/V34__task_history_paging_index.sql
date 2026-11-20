-- A task's history is keyset-paged on (date_completed, id_task_completion), so the index carries
-- both sort keys behind the task it narrows to. Without it every page sorts the task's whole
-- history to return twenty rows of it.
--
-- It pages on date_completed rather than due_date because a keyset has to order by a column that
-- cannot be null: comparing a null due date yields unknown, so an instance of an undated one-off
-- would be skipped by every cursor, silently, with the page before it reporting no next page.
CREATE INDEX idx_tc_task_completed
    ON task_completion (task_id, date_completed DESC, id_task_completion DESC);

-- Its every use is served by the new index's leading column.
DROP INDEX idx_tc_task_id;
