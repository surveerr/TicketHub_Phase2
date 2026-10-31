package com.tickethub.paymentservice.exception;

/** The Booking Service could not be reached in time (timeout, connection error, 5xx, open circuit). Maps to HTTP 503. */
public class BookingServiceUnavailableException extends RuntimeException {
    public BookingServiceUnavailableException(String message) {
        super(message);
    }

    public BookingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
