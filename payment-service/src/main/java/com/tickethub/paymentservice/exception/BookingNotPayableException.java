package com.tickethub.paymentservice.exception;

/** Business-rule conflict: the booking is not in a state that can be paid (not PENDING_PAYMENT, expired...). Maps to HTTP 409. */
public class BookingNotPayableException extends RuntimeException {
    public BookingNotPayableException(String message) {
        super(message);
    }
}
