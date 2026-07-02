package com.tasklean.api.domain.category.dto;

import com.tasklean.api.domain.category.Category;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private String color;
    private String icon;
    private Boolean isActive;
    private Long groupId;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getIdCategory())
                .name(category.getName())
                .color(category.getColor())
                .icon(category.getIcon())
                .isActive(category.getIsActive())
                .groupId(category.getGroup().getIdGroup())
                .dateCreated(category.getDateCreated())
                .dateUpdated(category.getDateUpdated())
                .build();
    }
}
