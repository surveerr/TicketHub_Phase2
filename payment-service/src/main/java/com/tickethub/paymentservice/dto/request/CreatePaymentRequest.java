package com.tickethub.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Only the booking id is accepted from the client. The owner, amount and currency are read from the
 * Booking Service so a client can never choose what it pays (Blueprint rule; backlog ST-46/ST-54).
 */
public record CreatePaymentRequest(
        @NotNull Long bookingId,
        @Size(max = 64) String idempotencyKey
) {
}
