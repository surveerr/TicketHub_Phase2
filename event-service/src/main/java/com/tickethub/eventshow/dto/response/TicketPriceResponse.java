package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.TicketCategory;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
public class TicketPriceResponse {
    private TicketCategory ticketCategory;
    private BigDecimal price;
}
