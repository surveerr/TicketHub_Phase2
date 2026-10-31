package com.tickethub.booking.repository;

import com.tickethub.booking.entity.BookingInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingInboxRepository extends JpaRepository<BookingInbox, Long> {
    boolean existsByEventId(String eventId);
    Optional<BookingInbox> findByEventId(String eventId);
}
