package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
public class ShowResponse {
    private Long id;
    private Long eventId;
    private Long venueId;
    private Long screenId;
    private String eventTitle;
    private String venueName;
    private String city;
    private String screenName;
    private Language language;
    private Instant startTime;
    private Instant endTime;
    private ShowFormat showFormat;
    private ShowStatus status;
}
