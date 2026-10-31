package com.tickethub.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Message shape for booking notifications (confirmed / payment-failed / cancelled).
 * The topic decides which e-mail the Notification Service sends.
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
        String showTime,
        String paymentId,
        String reason,
        Long aggregateVersion,
        String eventType
) {
    public BookingConfirmedEvent(
            String eventId, Long bookingId, Long customerId, String recipientEmail,
            Long showId, List<Long> seatIds, BigDecimal amount, String currency,
            Instant timestamp, String showTime, String paymentId, String reason) {
        this(eventId, bookingId, customerId, recipientEmail, showId, seatIds, amount,
                currency, timestamp, showTime, paymentId, reason, 1L, "BookingConfirmed");
    }

    public BookingConfirmedEvent {
        if (aggregateVersion == null) aggregateVersion = 1L;
        if (eventType == null) eventType = "BookingConfirmed";
        if (timestamp == null) timestamp = Instant.now();
    }
}
