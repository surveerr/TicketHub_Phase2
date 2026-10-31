package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.entity.ShowSeat;
import com.tickethub.eventshow.enums.SeatStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    Page<ShowSeat> findByShowId(Long showId, Pageable pageable);

    Optional<ShowSeat> findByShowIdAndSeatId(Long showId, Long seatId);

    long countByShowIdAndStatus(Long showId, SeatStatus status);

    List<ShowSeat> findByShowIdAndHeldByBookingId(Long showId, Long bookingId);

    long countByShowIdAndSeatIdInAndHeldByBookingId(Long showId, Collection<Long> seatIds, Long bookingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ShowSeat s set s.status = com.tickethub.eventshow.enums.SeatStatus.HELD, " +
            "s.heldByBookingId = :bookingId, s.holdExpiresAt = :expiresAt " +
            "where s.show.id = :showId and s.seat.id in :seatIds and " +
            "s.status = com.tickethub.eventshow.enums.SeatStatus.AVAILABLE")
    int holdAvailableSeats(@Param("showId") Long showId, @Param("seatIds") Collection<Long> seatIds,
                           @Param("bookingId") Long bookingId, @Param("expiresAt") Instant expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ShowSeat s set s.status = com.tickethub.eventshow.enums.SeatStatus.AVAILABLE," +
            " s.heldByBookingId = null, s.holdExpiresAt = null " +
            "where s.status = com.tickethub.eventshow.enums.SeatStatus.HELD and" +
            " s.holdExpiresAt < :now")
    int releaseExpired(@Param("now") Instant now);
}
