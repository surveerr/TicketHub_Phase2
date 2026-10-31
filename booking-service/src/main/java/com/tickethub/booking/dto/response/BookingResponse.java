package com.tickethub.booking.dto.response;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingSeat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(Long bookingId, Long customerId, String customerEmail, Long showId, BigDecimal amount, String currency, String status, Instant createdAt, Instant expiryAt, List<BookingSeatResponse> seats) {
    public static BookingResponse from(Booking b, List<BookingSeat> seats){
        return new BookingResponse(b.getId(), b.getCustomerId(), b.getCustomerEmail(), b.getShowId(), b.getAmount(), b.getCurrency(), b.getStatus().name(), b.getCreatedAt(), b.getExpiryAt(), seats.stream().map(s -> new BookingSeatResponse(s.getSeatId(), s.getPrice(), s.getStatus().name())).toList());
    }
}
record BookingSeatResponse(Long seatId, BigDecimal price, String status) {}
