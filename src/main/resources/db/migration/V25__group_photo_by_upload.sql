-- A group's picture is now an upload of ours, addressed by the upload's uid, rather than any URL
-- somebody could paste. A free-text URL was an open image proxy and a tracking vector, and there
-- was no way to tell a real photo from a link to anywhere.
--
-- Existing values are discarded: they are external links, which is exactly what is being dropped.
ALTER TABLE "group" DROP COLUMN photo_url;

ALTER TABLE "group" ADD COLUMN photo_uid VARCHAR(100);

-- The column holds what the API publishes, so the reference costs no join on a group read.
ALTER TABLE "group"
    ADD CONSTRAINT fk_group_photo_upload FOREIGN KEY (photo_uid) REFERENCES upload (uid);
