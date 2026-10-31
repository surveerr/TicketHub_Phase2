package com.tickethub.booking.repository;
import com.tickethub.booking.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
public interface BookingRepository extends JpaRepository<Booking,Long> {
    Optional<Booking> findByIdempotencyKey(String key);
    List<Booking> findByStatusAndExpiryAtBefore(BookingStatus status, Instant now);
}
