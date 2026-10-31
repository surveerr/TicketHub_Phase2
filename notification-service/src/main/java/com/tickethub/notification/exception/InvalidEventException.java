package com.tickethub.notification.exception;

/** Thrown for a malformed Kafka message. Not retried - goes straight to the DLT. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
