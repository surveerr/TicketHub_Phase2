package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.TicketCategory;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
public class UpdateSeatRequest {
    @NotNull
    private TicketCategory ticketCategory;
}
