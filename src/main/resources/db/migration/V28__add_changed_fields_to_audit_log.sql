-- An update entry said only that something was edited: a rename, a description change and a photo
-- swap all landed as the same row. This names the fields the update touched. Names only, never
-- values - a group trail is read by every member. Nullable, since non-update events and every
-- existing row leave it unset, which is distinct from an update that changed nothing (no row at
-- all). No GIN index - nothing filters on it, and an unread index still costs on every insert.
ALTER TABLE audit_log ADD COLUMN changed_fields JSONB;
