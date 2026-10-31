package com.tickethub.paymentservice.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSucceededEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String paymentReference,
        BigDecimal amount,
        String currency,
        String providerTransactionId,
        Long aggregateVersion,
        Instant timestamp
) {
    public PaymentSucceededEvent {
        if (eventType == null) eventType = "PaymentSucceeded";
        if (timestamp == null) timestamp = Instant.now();
        if (aggregateVersion == null) aggregateVersion = 1L;
    }
}
