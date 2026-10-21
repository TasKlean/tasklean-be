package com.tasklean.api.domain.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RenameDeviceRequest {

    @NotBlank
    @Size(max = 255)
    private String name;
}
