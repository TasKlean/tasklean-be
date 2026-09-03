-- Add the acting user to the audit trail so the actor is captured even for group-less
-- events (auth/account) and when the actor differs from the subject (e.g. one user
-- deleting another's account). Nullable: public auth events (login/register/logout)
-- have no authenticated caller in the security context.
ALTER TABLE audit_log
    ADD COLUMN actor_user_id BIGINT REFERENCES "user"(id_user);

CREATE INDEX idx_al_actor_user_id ON audit_log(actor_user_id);
