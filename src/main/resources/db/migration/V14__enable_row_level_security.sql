-- Enable Row Level Security on every table.
-- Supabase auto-exposes public-schema tables via its PostgREST API using the
-- public "anon" key. Enabling RLS with NO policies denies all access through
-- that API, closing the hole. Our Spring backend connects as the table owner,
-- which bypasses RLS, so application access is unaffected.
-- Do NOT add FORCE ROW LEVEL SECURITY (that would make the owner obey policies too).

ALTER TABLE "user"             ENABLE ROW LEVEL SECURITY;
ALTER TABLE device             ENABLE ROW LEVEL SECURITY;
ALTER TABLE "group"            ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_member       ENABLE ROW LEVEL SECURITY;
ALTER TABLE category           ENABLE ROW LEVEL SECURITY;
ALTER TABLE tag                ENABLE ROW LEVEL SECURITY;
ALTER TABLE task               ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_tag           ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_completion    ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification       ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log          ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_verification ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token      ENABLE ROW LEVEL SECURITY;
