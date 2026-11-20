-- Which chore a completion photo was signed for, so an attach can prove that proof taken for one
-- round is not being offered on another chore's.
--
-- No foreign key - on this column or the two beside it, whose constraints this drops. An upload's
-- target is provenance: a note of what was signed, once, not a live association the app navigates,
-- which is why audit_log.entity_id carries none either. The constraints bought referential
-- integrity on values this service resolves and validates itself, and charged a reference cycle for
-- it: "group" and "user" already point back at upload.uid for their photo, so neither row could
-- ever be hard-deleted while an upload named it. A dangling target is safe because the read rule
-- fails closed - it asks whether the caller is a member of that household, and nothing can answer
-- yes for a household that is gone.
ALTER TABLE upload
    ADD COLUMN task_id BIGINT;

ALTER TABLE upload
    DROP CONSTRAINT fk_upload_user;

ALTER TABLE upload
    DROP CONSTRAINT fk_upload_group;
