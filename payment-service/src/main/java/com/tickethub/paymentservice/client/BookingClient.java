package com.tickethub.paymentservice.client;

import com.tickethub.paymentservice.exception.BookingNotFoundException;
import com.tickethub.paymentservice.exception.BookingServiceUnavailableException;

/**
 * Payment Service -> Booking Service (REST). Exactly one implementation is active, chosen by booking.client.name
 * (STUB or REST).
 */
public interface BookingClient {

    /** Reads the booking (owner, amount snapshot, status, expiry). Safe to retry. */
    BookingDetails getBooking(Long bookingId)
            throws BookingNotFoundException, BookingServiceUnavailableException;

    /** Tells Booking the final payment result. Never throws; the caller decides what to do from the result. */
    BookingSyncResult notifyPaymentResult(Long bookingId, BookingPaymentResult result);
}
