package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.TicketCategory;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class UpdateTicketPricesRequest {
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
