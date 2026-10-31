package com.tickethub.eventshow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class UpdateLocationRequest {
    @NotBlank
    private String city;
    @NotBlank
    private String state;
    @NotBlank
    private String country;
}
