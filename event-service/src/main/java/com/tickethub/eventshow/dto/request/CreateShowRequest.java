package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.*;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Getter
@Setter
public class CreateShowRequest {
    @NotNull
    private Long eventId;
    @NotNull
    private Long venueId;
    @NotNull
    private Long screenId;
    @NotNull
    private Language language;
    @NotNull
    @Future
    private Instant startTime;
    @NotNull
    @Future
    private Instant endTime;
    private ShowFormat showFormat;
    @NotEmpty
    @Valid
    private List<TicketPriceRequest> tickets;

    @Getter
    @Setter
    public static class TicketPriceRequest {
        @NotNull
        private TicketCategory ticketCategory;
        @NotNull
        @DecimalMin("0.00")
        private BigDecimal price;
    }
}
