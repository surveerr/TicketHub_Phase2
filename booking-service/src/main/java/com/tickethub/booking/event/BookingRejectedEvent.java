package com.tickethub.booking.event;

import java.time.Instant;

public record BookingRejectedEvent(
        String eventId,
        String eventType,
        Long bookingId,
        String reason,
        Long aggregateVersion,
        Instant timestamp
) {
    public BookingRejectedEvent {
        if (eventType == null) eventType = "BookingRejected";
        if (timestamp == null) timestamp = Instant.now();
        if (aggregateVersion == null) aggregateVersion = 1L;
    }
}
