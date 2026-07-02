package com.tasklean.api.domain.groupmember.dto;

import com.tasklean.api.domain.groupmember.GroupMember;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberResponse {

    private Long id;
    private String role;
    private Boolean isActive;
    private LocalDateTime dateJoined;
    private LocalDateTime dateLeft;
    private Long userId;
    private Long groupId;
    private Long addedById;
    private Long removedById;

    public static GroupMemberResponse from(GroupMember member) {
        return GroupMemberResponse.builder()
                .id(member.getIdGroupMember())
                .role(member.getRole())
                .isActive(member.getIsActive())
                .dateJoined(member.getDateJoined())
                .dateLeft(member.getDateLeft())
                .userId(member.getUser().getIdUser())
                .groupId(member.getGroup().getIdGroup())
                .addedById(member.getAddedBy() != null ? member.getAddedBy().getIdGroupMember() : null)
                .removedById(member.getRemovedBy() != null ? member.getRemovedBy().getIdGroupMember() : null)
                .build();
    }
}
