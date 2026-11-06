-- The audit feeds page by keyset on (date_created DESC, id_audit_log DESC) within a scope, so the
-- scope column alone is no longer the right leading key: without the sort columns in the index,
-- resuming a page means sorting the whole scope. Each composite covers its old index as a prefix,
-- which is why those are dropped rather than kept alongside - an unread index still costs on every
-- insert, and this table is append-only.
DROP INDEX idx_al_group_id;

CREATE INDEX idx_al_group_keyset ON audit_log (group_id, date_created DESC, id_audit_log DESC);

DROP INDEX idx_al_entity;

CREATE INDEX idx_al_entity_keyset ON audit_log (entity_type, entity_id, date_created DESC, id_audit_log DESC);
