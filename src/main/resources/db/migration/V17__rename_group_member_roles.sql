-- Rename group roles to disambiguate them from platform roles (User.role):
-- ADMIN -> GROUP_ADMIN, MEMBER -> GROUP_MEMBER. Drop the old CHECK, migrate the
-- existing values, then add the CHECK back with the new vocabulary.
ALTER TABLE group_member DROP CONSTRAINT group_member_role_check;

UPDATE group_member SET role = 'GROUP_ADMIN' WHERE role = 'ADMIN';
UPDATE group_member SET role = 'GROUP_MEMBER' WHERE role = 'MEMBER';

ALTER TABLE group_member
    ADD CONSTRAINT group_member_role_check CHECK (role IN ('GROUP_ADMIN', 'GROUP_MEMBER'));
