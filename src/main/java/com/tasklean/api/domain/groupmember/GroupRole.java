package com.tasklean.api.domain.groupmember;

/**
 * A member's role within a single group — distinct from a user's platform-wide role
 * ({@code User.role}). Stored as the enum name in {@code group_member.role}.
 */
public enum GroupRole {

    // Manages the group: edit group details, invite/remove members, change roles, manage categories and tags.
    GROUP_ADMIN,

    // Participates in the group: create/edit own tasks, complete tasks, view group content, leave the group.
    GROUP_MEMBER
}
