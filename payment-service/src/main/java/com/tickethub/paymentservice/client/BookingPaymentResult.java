package com.tickethub.paymentservice.client;

/**
 * Body sent to Booking's POST /internal/v1/bookings/{id}/payment-result when a payment reaches a final state.
 * status is SUCCESS or FAILED; failureReason is only set for FAILED. Booking must treat repeated calls with the same
 * paymentReference as idempotent.
 */
public record BookingPaymentResult(
        String paymentReference,
        String status,
        String providerTransactionId,
        String failureReason
) {
}
