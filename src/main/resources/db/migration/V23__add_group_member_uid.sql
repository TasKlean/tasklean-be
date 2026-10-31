-- A membership is now addressed by uid in the REST API (/api/group-members/{memberUid}),
-- so its internal primary key stays internal like every other entity's.
ALTER TABLE group_member ADD COLUMN uid VARCHAR(500);

UPDATE group_member SET uid = gen_random_uuid()::text WHERE uid IS NULL;

ALTER TABLE group_member ALTER COLUMN uid SET NOT NULL;
ALTER TABLE group_member ADD CONSTRAINT unique_group_member_uid UNIQUE (uid);

CREATE INDEX idx_gm_uid ON group_member (uid);
