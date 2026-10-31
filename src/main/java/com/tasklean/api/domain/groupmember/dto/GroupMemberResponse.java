package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupRole;
import com.tasklean.api.domain.user.dto.UserSummaryResponse;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A membership as returned by the API. It carries the member's user summary rather than ids, so a
 * client can render a household roster from this one response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberResponse {

    private String uid;
    private GroupRole role;
    private Boolean isActive;
    private LocalDateTime dateJoined;
    private LocalDateTime dateLeft;
    private UserSummaryResponse user;
    private UserSummaryResponse addedBy;
    private UserSummaryResponse removedBy;

    public static GroupMemberResponse from(GroupMember member) {
        return GroupMemberResponse.builder()
                .uid(member.getUid())
                .role(member.getRole())
                .isActive(member.getIsActive())
                .dateJoined(member.getDateJoined())
                .dateLeft(member.getDateLeft())
                .user(UserSummaryResponse.from(member.getUser()))
                // added_by/removed_by reference the acting membership; a client wants the person.
                .addedBy(actor(member.getAddedBy()))
                .removedBy(actor(member.getRemovedBy()))
                .build();
    }

    private static UserSummaryResponse actor(GroupMember acting) {
        return acting == null ? null : UserSummaryResponse.from(acting.getUser());
    }
}
