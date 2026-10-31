package com.tickethub.paymentservice.exception;

/** The Booking Service does not know this booking id. Maps to HTTP 404. */
public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(String message) {
        super(message);
    }
}
