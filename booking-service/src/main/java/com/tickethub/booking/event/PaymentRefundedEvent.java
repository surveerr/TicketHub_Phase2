package com.tickethub.booking.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRefundedEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String paymentReference,
        String refundId,
        BigDecimal amount,
        String currency,
        Long aggregateVersion,
        Instant timestamp
) {
    public PaymentRefundedEvent {
        if (eventType == null) eventType = "PaymentRefunded";
        if (timestamp == null) timestamp = Instant.now();
        if (aggregateVersion == null) aggregateVersion = 1L;
    }
}
