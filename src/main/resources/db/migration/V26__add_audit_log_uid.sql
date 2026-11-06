-- The audit trail is the one readable table whose rows had no external identifier: the API had to
-- hand out id_audit_log, which every other endpoint treats as internal. Backfilled rather than
-- defaulted, so existing entries get one too and the column can be NOT NULL.
ALTER TABLE audit_log
    ADD COLUMN uid VARCHAR(100);

UPDATE audit_log
SET uid = gen_random_uuid()::text
WHERE uid IS NULL;

ALTER TABLE audit_log
    ALTER COLUMN uid SET NOT NULL,
    ADD CONSTRAINT audit_log_uid_key UNIQUE (uid);
