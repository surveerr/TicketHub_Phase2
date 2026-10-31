package com.tickethub.eventshow.dto.response;

import lombok.*;

import java.util.*;

@Getter
@Setter
public class ShowDetailResponse extends ShowResponse {
    private long availableSeats;
    private long heldSeats;
    private long bookedSeats;
    private long blockedSeats;
    private List<TicketPriceResponse> tickets;
}
