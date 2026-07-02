package com.tasklean.api.domain.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagRequest {

    @NotBlank
    private String name;

    private String color;

    @NotNull
    private Long groupId;
}
