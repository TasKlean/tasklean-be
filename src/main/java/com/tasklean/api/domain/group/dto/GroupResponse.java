package com.tasklean.api.domain.group.dto;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.groupmember.GroupRole;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A group as returned by the API. The invite code is a shareable join secret, so it is exposed
 * only through {@link #forAdmin(Group, long)} — for callers allowed to invite.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupResponse {

    private String uid;
    private String name;
    private String description;
    private String photoUrl;
    private String inviteCode;
    private GroupRole myRole;
    private Integer memberCount;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    /** Without the invite code or a role — the safe default for a caller who is not a member. */
    public static GroupResponse from(Group group, long memberCount) {
        return base(group, memberCount).build();
    }

    /**
     * As seen by one of the group's members: carries their own role so a client knows which
     * controls to show, and the invite code only when that role is {@code GROUP_ADMIN}.
     */
    public static GroupResponse forMember(Group group, GroupRole myRole, long memberCount) {
        GroupResponseBuilder builder = base(group, memberCount).myRole(myRole);
        // Inviting is a GROUP_ADMIN power, so only an admin is handed the code to share.
        return myRole == GroupRole.GROUP_ADMIN
                ? builder.inviteCode(group.getInviteCode()).build()
                : builder.build();
    }

    /** With the invite code, for a caller allowed to invite but whose own role is not in hand. */
    public static GroupResponse forAdmin(Group group, long memberCount) {
        return base(group, memberCount).inviteCode(group.getInviteCode()).build();
    }

    private static GroupResponseBuilder base(Group group, long memberCount) {
        return GroupResponse.builder()
                .uid(group.getUid())
                .name(group.getName())
                .description(group.getDescription())
                .photoUrl(group.getPhotoUrl())
                .memberCount((int) memberCount)
                .isActive(group.getIsActive())
                .dateCreated(group.getDateCreated())
                .dateUpdated(group.getDateUpdated());
    }
}
