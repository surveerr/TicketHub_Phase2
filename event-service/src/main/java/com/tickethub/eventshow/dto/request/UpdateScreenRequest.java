package com.tickethub.eventshow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class UpdateScreenRequest {
    @NotBlank
    private String name;
}
