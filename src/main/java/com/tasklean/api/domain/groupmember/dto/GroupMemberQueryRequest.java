package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.domain.groupmember.MembershipStatus;
import lombok.*;

/**
 * What a caller may ask of a group's roster. Bound from the query string as an object rather than
 * as a loose parameter, so the narrowing has somewhere to grow without changing the signature.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberQueryRequest {

    /**
     * Which memberships to return. Omitted, the roster is the people currently in the group -
     * {@code ALL} also returns everybody who has left, which is how a client offers them as a
     * filter on the group's audit trail.
     */
    private MembershipStatus status = MembershipStatus.ACTIVE;
}
