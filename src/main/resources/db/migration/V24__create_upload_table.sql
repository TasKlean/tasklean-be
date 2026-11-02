-- One row per upload the API has signed for. It exists so an attach can prove the object was
-- really requested by this caller for this target, and so the sweep can find an object nothing
-- ever pointed at without listing the whole bucket.
--
-- The storage key stays internal, like every other entity's primary key: the API publishes the uid.
CREATE TABLE upload (
                        id_upload BIGSERIAL PRIMARY KEY,
                        uid VARCHAR(100) UNIQUE NOT NULL,
                        storage_key VARCHAR(200) UNIQUE NOT NULL,
                        purpose VARCHAR(50) NOT NULL,
                        content_type VARCHAR(100) NOT NULL,
                        size_bytes BIGINT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        user_id BIGINT NOT NULL,
                        group_id BIGINT,
                        date_attached TIMESTAMP,
                        date_created TIMESTAMP DEFAULT NOW() NOT NULL,
                        date_updated TIMESTAMP DEFAULT NOW(),
                        CONSTRAINT fk_upload_user FOREIGN KEY (user_id) REFERENCES "user" (id_user),
                        CONSTRAINT fk_upload_group FOREIGN KEY (group_id) REFERENCES "group" (id_group),
    -- Lists every purpose the design covers; the Java enum is what gates which are callable today,
    -- so adding the task and avatar purposes needs no migration.
                        CONSTRAINT upload_purpose_check CHECK (purpose IN
                            ('GROUP_PHOTO', 'TASK_PHOTO', 'COMPLETION_PHOTO', 'USER_PHOTO')),
    -- An upload is never deleted, it ends in a terminal state - hence a status rather than the
    -- is_active flag the other tables carry: there are four meaningful states here, not two.
                        CONSTRAINT upload_status_check CHECK (status IN
                            ('PENDING', 'ATTACHED', 'SUPERSEDED', 'DELETED'))
);

CREATE INDEX idx_upload_uid ON upload (uid);
-- The sweep asks for one status older than a cutoff, which is exactly this index.
CREATE INDEX idx_upload_status_created ON upload (status, date_created);

ALTER TABLE upload ENABLE ROW LEVEL SECURITY;
