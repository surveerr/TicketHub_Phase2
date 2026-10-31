package com.tickethub.booking.repository;
import com.tickethub.booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BookingSeatRepository extends JpaRepository<BookingSeat,Long> { List<BookingSeat> findByBookingId(Long bookingId); }
