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
    private String groupUid;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .id(tag.getIdTag())
                .name(tag.getName())
                .color(tag.getColor())
                .groupUid(tag.getGroup().getUid())
                .dateCreated(tag.getDateCreated())
                .dateUpdated(tag.getDateUpdated())
                .build();
    }
}
