package com.tickethub.eventshow.dto.response;

import lombok.*;

import java.util.*;

@Getter
@Setter
public class SeatActionResponse {
    private Long showId;
    private Long bookingId;
    private List<Long> seatIds;
    private String status;
}
