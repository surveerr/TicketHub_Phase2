package com.tickethub.eventshow.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class UpdateVenueRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String addressLine;
    private String pincode;
    private Double latitude;
    private Double longitude;
    @NotNull
    private Long locationId;
}
