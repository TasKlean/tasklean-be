package com.tasklean.api.domain.group.dto;

import com.tasklean.api.domain.group.Group;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A group as returned by the API. The invite code is a shareable join secret, so it is exposed
 * only through {@link #withInviteCode(Group)} — for callers allowed to invite.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupResponse {

    private Long id;
    private String uid;
    private String name;
    private String description;
    private String photoUrl;
    private String inviteCode;
    private Boolean isActive;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    /** Without the invite code — the safe default for anyone who may not invite. */
    public static GroupResponse from(Group group) {
        return base(group).build();
    }

    /** With the invite code, for a caller allowed to invite members (a group admin). */
    public static GroupResponse withInviteCode(Group group) {
        return base(group).inviteCode(group.getInviteCode()).build();
    }

    private static GroupResponseBuilder base(Group group) {
        return GroupResponse.builder()
                .id(group.getIdGroup())
                .uid(group.getUid())
                .name(group.getName())
                .description(group.getDescription())
                .photoUrl(group.getPhotoUrl())
                .isActive(group.getIsActive())
                .dateCreated(group.getDateCreated())
                .dateUpdated(group.getDateUpdated());
    }
}
