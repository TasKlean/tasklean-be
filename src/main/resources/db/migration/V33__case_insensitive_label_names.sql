-- A category's or tag's name is unique per household REGARDLESS OF CASE. A member typing
-- "kitchen" where "Kitchen" already exists must land on the one that is there rather than create a
-- second one beside it, and the plain unique constraints could not express that: they compared
-- byte for byte, so the two coexisted and a typeahead offered both.
ALTER TABLE category DROP CONSTRAINT unique_category_per_group;
ALTER TABLE tag DROP CONSTRAINT unique_tag_per_group;

-- Deliberately NOT partial on is_active. A soft-deleted name stays reserved, which is what makes
-- re-creating one revive the original row instead of inserting a twin: the tasks still pointing at
-- it get their classification back, and the name keeps one continuous audit trail.
CREATE UNIQUE INDEX uq_category_group_name ON category (group_id, lower(name));
CREATE UNIQUE INDEX uq_tag_group_name ON tag (group_id, lower(name));
