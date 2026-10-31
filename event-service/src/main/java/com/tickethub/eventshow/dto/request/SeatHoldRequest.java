package com.tickethub.eventshow.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class SeatHoldRequest {
    @NotNull
    private Long bookingId;
    @NotEmpty
    @Size(max = 10)
    private List<@NotNull Long> seatIds;
    private Instant holdExpiresAt;
}
