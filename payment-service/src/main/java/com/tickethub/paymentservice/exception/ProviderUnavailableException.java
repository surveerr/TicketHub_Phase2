package com.tickethub.paymentservice.exception;

/** The payment provider is slow, down, or its circuit breaker is open. Maps to HTTP 503 (a controlled failure, SC-05/SC-16). */
public class ProviderUnavailableException extends PaymentFailedException {
    public ProviderUnavailableException(String message) {
        super(message);
    }
}
