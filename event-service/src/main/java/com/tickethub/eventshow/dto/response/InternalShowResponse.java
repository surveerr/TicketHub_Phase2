package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.EventStatus;
import com.tickethub.eventshow.enums.ShowStatus;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Getter
@Setter
public class InternalShowResponse {
    private Long showId;
    private ShowStatus status;
    private EventStatus eventStatus;
    private Instant startTime;
    private Instant endTime;
    private List<TicketPriceResponse> tickets;
}
