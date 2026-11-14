-- A user's picture can now be an upload of ours, addressed by the upload's uid, the same way a
-- group's is (V25). Unlike V25 this only adds: photo_url stays, because a user has a second and
-- legitimate source for it - the picture Google hands over at sign-in - and photo_uid wins over
-- that one when both are set. Nothing is backfilled; an existing Google picture keeps showing.
ALTER TABLE "user" ADD COLUMN photo_uid VARCHAR(100);

-- The column holds what the API publishes, so the reference costs no join on a profile read.
ALTER TABLE "user"
    ADD CONSTRAINT fk_user_photo_upload FOREIGN KEY (photo_uid) REFERENCES upload (uid);
