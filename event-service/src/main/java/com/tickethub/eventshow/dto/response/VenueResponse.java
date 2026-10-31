package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.ResourceStatus;
import lombok.*;

@Getter
@Setter
public class VenueResponse {
    private Long id;
    private Long locationId;
    private String city;
    private String name;
    private String addressLine;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private ResourceStatus status;
}
