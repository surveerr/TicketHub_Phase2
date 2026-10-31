package com.tickethub.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Message carried on the Kafka topics (confirmed / cancelled / payment-failed).
 *
 * REQUIRED : bookingId, recipientEmail
 * OPTIONAL : everything else (the e-mail simply shows what is present).
 *
 * Unknown JSON fields are ignored, so booking/payment service can add fields freely.
 */
public record BookingConfirmedEvent(
        String eventId,
        Long bookingId,
        Long customerId,
        String recipientEmail,
        Long showId,
        List<Long> seatIds,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        // ---- optional, nicer e-mail ----
        String customerName,
        String eventName,
        String venue,
        String showTime,
        List<String> seatNumbers,
        String paymentId,
        String reason,
        String seatCategory,
        BigDecimal totalAmount,
        Long aggregateVersion
) {
    public BigDecimal effectiveAmount() {
        return totalAmount != null ? totalAmount : amount;
    }
}
