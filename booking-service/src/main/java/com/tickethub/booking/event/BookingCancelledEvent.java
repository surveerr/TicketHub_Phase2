package com.tickethub.booking.event;

import java.time.Instant;

public record BookingCancelledEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String reason,
        Long aggregateVersion,
        Instant timestamp
) {
    public BookingCancelledEvent {
        if (eventType == null) eventType = "BookingCancelled";
        if (timestamp == null) timestamp = Instant.now();
        if (aggregateVersion == null) aggregateVersion = 1L;
    }
}
