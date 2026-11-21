package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.user.dto.UserSummaryResponse;
import lombok.*;

/**
 * A person as seen inside a household, keyed by their membership — which is what every write in a
 * group scope takes, so what a client reads off a task can be sent straight back.
 *
 * <p>Deliberately carries no user uid. A member cannot read another account
 * ({@code GET /api/users/{uid}} is admin-or-self) and no group-scoped write accepts one, so
 * publishing a globally stable id would only let anyone sharing a household correlate the same
 * person across all of them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSummaryResponse {

    private String memberUid;
    private String name;
    private String lastName;
    private String photoUid;
    private String photoUrl;

    public static MemberSummaryResponse from(GroupMember membership) {
        if (membership == null) {
            return null;
        }
        // Through UserSummaryResponse rather than beside it, so which of the two pictures an
        // account publishes stays decided in exactly one place.
        UserSummaryResponse person = UserSummaryResponse.from(membership.getUser());
        return MemberSummaryResponse.builder()
                .memberUid(membership.getUid())
                .name(person.getName())
                .lastName(person.getLastName())
                .photoUid(person.getPhotoUid())
                .photoUrl(person.getPhotoUrl())
                .build();
    }
}
