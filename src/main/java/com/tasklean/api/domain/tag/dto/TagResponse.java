package com.tasklean.api.domain.tag.dto;

import com.tasklean.api.domain.tag.Tag;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagResponse {

    private Long id;
    private String name;
    private String color;
    private Boolean isActive;
    private Long groupId;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .id(tag.getIdTag())
                .name(tag.getName())
                .color(tag.getColor())
                .isActive(tag.getIsActive())
                .groupId(tag.getGroup().getIdGroup())
                .dateCreated(tag.getDateCreated())
                .dateUpdated(tag.getDateUpdated())
                .build();
    }
}
