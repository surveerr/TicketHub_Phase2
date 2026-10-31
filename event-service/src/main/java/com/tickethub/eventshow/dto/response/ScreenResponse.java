package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.ResourceStatus;
import lombok.*;

@Getter
@Setter
public class ScreenResponse {
    private Long id;
    private Long venueId;
    private String name;
    private Integer totalSeats;
    private ResourceStatus status;
}
