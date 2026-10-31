package com.tickethub.paymentservice.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment Service's OWN view of a booking (its own DTO - entities are never shared between services).
 * Matches Booking Service's BookingResponse: { bookingId, customerId, showId, amount, currency, status,
 * createdAt, expiryAt, seats }. The Booking Service is the source of truth for who owns the booking and how much
 * must be paid. Unknown fields (showId, seats...) are ignored so Booking can add fields without breaking this service.
 * Times are UTC instants (e.g. 2026-10-05T12:00:00Z).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingDetails(
        @JsonAlias({"id"}) Long bookingId,
        @JsonAlias({"userId"}) Long customerId,
        BigDecimal amount,
        String currency,
        String status,
        @JsonAlias({"expiryAt"}) Instant expiresAt
) {
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
}
