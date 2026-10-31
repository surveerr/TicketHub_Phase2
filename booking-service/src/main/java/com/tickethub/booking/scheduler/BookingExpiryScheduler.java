package com.tickethub.booking.scheduler;
import com.tickethub.booking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component public class BookingExpiryScheduler { private final BookingService service; public BookingExpiryScheduler(BookingService s){service=s;} @Scheduled(fixedDelayString="${booking.expiry-check-ms:30000}") public void expire(){service.expireBookings();} }
