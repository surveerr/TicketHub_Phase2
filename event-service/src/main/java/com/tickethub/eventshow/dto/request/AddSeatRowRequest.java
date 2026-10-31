package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.TicketCategory;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class AddSeatRowRequest {
    @NotBlank
    private String rowLabel;
    @NotNull
    @Positive
    private Integer seatsPerRow;
    @NotNull
    private TicketCategory ticketCategory;
}
