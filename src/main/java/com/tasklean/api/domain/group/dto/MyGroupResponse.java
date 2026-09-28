package com.tasklean.api.domain.group.dto;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupRole;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A group as seen by one of its members, carrying that member's own role so a client can decide
 * which admin controls to show. The invite code is present only for a group admin.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyGroupResponse {

    private Long id;
    private String uid;
    private String name;
    private String description;
    private String photoUrl;
    private String inviteCode;
    private GroupRole myRole;
    private LocalDateTime dateJoined;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static MyGroupResponse from(GroupMember membership) {
        Group group = membership.getGroup();
        boolean isAdmin = membership.getRole() == GroupRole.GROUP_ADMIN;
        return MyGroupResponse.builder()
                .id(group.getIdGroup())
                .uid(group.getUid())
                .name(group.getName())
                .description(group.getDescription())
                .photoUrl(group.getPhotoUrl())
                // Inviting is a GROUP_ADMIN power, so only an admin is handed the code to share.
                .inviteCode(isAdmin ? group.getInviteCode() : null)
                .myRole(membership.getRole())
                .dateJoined(membership.getDateJoined())
                .dateCreated(group.getDateCreated())
                .dateUpdated(group.getDateUpdated())
                .build();
    }
}
