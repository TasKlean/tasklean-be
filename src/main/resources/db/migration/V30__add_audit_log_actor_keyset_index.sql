-- A group trail can now be narrowed to one actor, and that feed pages by keyset like every other,
-- so the sort columns belong in the index behind the two equality columns - otherwise resuming a
-- page means sorting everything the actor ever did in the group. idx_al_actor_user_id stays: the
-- account scope and the visibility rule read the actor without a group, which this does not cover.
CREATE INDEX idx_al_group_actor_keyset
    ON audit_log (group_id, actor_user_id, date_created DESC, id_audit_log DESC);
