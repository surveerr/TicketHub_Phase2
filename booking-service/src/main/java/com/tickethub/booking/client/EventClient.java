package com.tickethub.booking.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface EventClient {
    ShowInfo getShow(Long showId);
    List<SeatInfo> getShowSeats(Long showId);
    void holdSeats(Long showId, Long bookingId, List<Long> seatIds, Instant expiresAt);
    void confirmSeats(Long showId, Long bookingId);
    void releaseSeats(Long showId, Long bookingId);
    record ShowInfo(Long showId, String status, String eventStatus, Instant startTime, Instant endTime) {}
    record SeatInfo(Long seatId, String status, BigDecimal price) {}
}
