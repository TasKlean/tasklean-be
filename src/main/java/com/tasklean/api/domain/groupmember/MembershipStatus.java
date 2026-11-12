package com.tasklean.api.domain.groupmember;

/**
 * Which memberships a roster read returns. A membership is never deleted, only deactivated, so the
 * group keeps a row for everybody who was ever in it - {@code FORMER} and {@code ALL} are how a
 * caller reaches the people who have since left.
 */
public enum MembershipStatus {

    /** Only the people currently in the group. */
    ACTIVE,

    /** Only the people who have left or been removed. */
    FORMER,

    /** Everybody who was ever in the group, current or not. */
    ALL
}
