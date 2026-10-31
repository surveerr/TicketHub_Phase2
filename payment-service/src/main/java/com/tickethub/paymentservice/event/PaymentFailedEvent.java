package com.tickethub.paymentservice.event;

import java.time.Instant;

public record PaymentFailedEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String paymentReference,
        String failureReason,
        Long aggregateVersion,
        Instant timestamp
) {
    public PaymentFailedEvent {
        if (eventType == null) eventType = "PaymentFailed";
        if (timestamp == null) timestamp = Instant.now();
        if (aggregateVersion == null) aggregateVersion = 1L;
    }
}
