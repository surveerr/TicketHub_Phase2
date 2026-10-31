package com.tickethub.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateBookingRequest(@NotNull Long showId, @NotEmpty @Size(max=10) List<@NotNull Long> seatIds) {}
