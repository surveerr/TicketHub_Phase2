package com.tickethub.paymentservice.provider;

/** Unchecked wrapper for any failure talking to the payment provider (counted by the circuit breaker). */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
