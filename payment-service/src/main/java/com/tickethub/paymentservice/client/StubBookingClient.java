package com.tickethub.paymentservice.client;

import com.tickethub.paymentservice.exception.BookingNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Stand-in for the Booking Service so this service runs and can be tested alone (BOOKING_CLIENT=STUB, the default).
 *  - every booking is PENDING_PAYMENT with the configured amount/currency/customer
 *  - booking id 404 -> not found, booking id 409 -> already CONFIRMED (not payable), booking id 410 -> expired
 */
@Component
@ConditionalOnProperty(prefix = "booking.client", name = "name", havingValue = "STUB", matchIfMissing = true)
public class StubBookingClient implements BookingClient {

    private static final Logger log = LoggerFactory.getLogger(StubBookingClient.class);

    private final BigDecimal amount;
    private final String currency;
    private final Long customerId;

    public StubBookingClient(@Value("${booking.stub.amount:499.00}") BigDecimal amount,
                             @Value("${booking.stub.currency:INR}") String currency,
                             @Value("${booking.stub.customer-id:1}") Long customerId) {
        this.amount = amount;
        this.currency = currency;
        this.customerId = customerId;
    }

    @Override
    public BookingDetails getBooking(Long bookingId) {
        if (bookingId == 404L) {
            throw new BookingNotFoundException("Booking not found: " + bookingId);
        }
        String status = bookingId == 409L ? "CONFIRMED" : BookingDetails.PENDING_PAYMENT;
        Instant expiresAt = bookingId == 410L
                ? Instant.now().minus(Duration.ofMinutes(1))
                : Instant.now().plus(Duration.ofMinutes(15));
        return new BookingDetails(bookingId, customerId, amount, currency, status, expiresAt);
    }

    @Override
    public BookingSyncResult notifyPaymentResult(Long bookingId, BookingPaymentResult result) {
        log.info("[STUB booking] booking {} told payment {} -> {}", bookingId, result.paymentReference(), result.status());
        return BookingSyncResult.DELIVERED;
    }
}
