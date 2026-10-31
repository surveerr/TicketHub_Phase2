package com.tickethub.eventshow.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
public class SeatBookingActionRequest {
    @NotNull
    private Long bookingId;
}
