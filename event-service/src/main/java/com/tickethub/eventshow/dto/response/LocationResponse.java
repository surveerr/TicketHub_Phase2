package com.tickethub.eventshow.dto.response;

import lombok.*;

@Getter
@Setter
public class LocationResponse {
    private Long id;
    private String city;
    private String state;
    private String country;
}
