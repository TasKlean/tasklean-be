-- V28 stored the changed fields as a list of names; a field whose value is safe to keep now carries
-- it too, so each element becomes an object. Converting the existing rows keeps one shape in the
-- column rather than leaving readers to handle both - production has no rows yet, so this is a
-- no-op there and a handful of rows in development.
UPDATE audit_log
SET changed_fields = (
        SELECT jsonb_agg(jsonb_build_object('field', name))
        FROM jsonb_array_elements_text(changed_fields) AS name
    )
WHERE changed_fields IS NOT NULL;
