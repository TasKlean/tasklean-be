package com.tasklean.api.domain.group;

/**
 * The orderings a caller may ask the group list for. Name is the tiebreak behind every other one,
 * so two groups sharing a date or a member count keep a stable order between calls.
 */
public enum GroupSort {

    NAME_ASC,

    NAME_DESC,

    // Newest first, for all three of these: a list is read from the top, and the recent end is
    // what a caller asking to be ordered by a date or a count wants to see there.
    JOINED_DESC,

    CREATED_DESC,

    MEMBERS_DESC
}
