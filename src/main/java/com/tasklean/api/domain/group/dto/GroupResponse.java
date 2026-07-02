package com.tasklean.api.domain.group.dto;

import com.tasklean.api.domain.group.Group;
import lombok.*;

import java.time.LocalDateTime;

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

    public static GroupResponse from(Group group) {
        return GroupResponse.builder()
                .id(group.getIdGroup())
                .uid(group.getUid())
                .name(group.getName())
                .description(group.getDescription())
                .photoUrl(group.getPhotoUrl())
                .inviteCode(group.getInviteCode())
                .isActive(group.getIsActive())
                .dateCreated(group.getDateCreated())
                .dateUpdated(group.getDateUpdated())
                .build();
    }
}
