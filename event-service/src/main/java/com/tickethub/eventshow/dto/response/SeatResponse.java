package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
public class SeatResponse {
    private Long id;
    private Long showSeatId;
    private String rowLabel;
    private Integer seatNumber;
    private TicketCategory ticketCategory;
    private SeatStatus status;
    private BigDecimal price;
}
