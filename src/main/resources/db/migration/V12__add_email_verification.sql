-- Add email verification flag to user
ALTER TABLE "user" ADD COLUMN is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Email verification codes table
CREATE TABLE email_verification (
    id_email_verification BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES "user"(id_user) ON DELETE CASCADE,
    code                 VARCHAR(6) NOT NULL,
    expires_at           TIMESTAMP NOT NULL,
    date_created         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verification_user_id ON email_verification(user_id);
CREATE INDEX idx_email_verification_expires_at ON email_verification(expires_at);
