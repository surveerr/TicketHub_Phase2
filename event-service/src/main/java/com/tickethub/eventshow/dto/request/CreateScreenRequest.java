package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.TicketCategory;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class CreateScreenRequest {
    @NotBlank
    private String name;
    @NotNull
    @Positive
    private Integer rows;
    @NotNull
    @Positive
    private Integer seatsPerRow;
    @NotNull
    private TicketCategory ticketCategory;
}
