package com.tickethub.booking.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
public record CreateBookingResponse(Long bookingId, Long showId, BigDecimal amount, String currency, String status, Instant expiresAt, String message) {}
