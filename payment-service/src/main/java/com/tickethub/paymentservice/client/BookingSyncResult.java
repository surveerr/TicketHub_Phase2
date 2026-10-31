package com.tickethub.paymentservice.client;

/** Outcome of telling the Booking Service about a payment result. */
public enum BookingSyncResult {
    /** Booking accepted the result. */
    DELIVERED,
    /** Booking answered but refused it (unknown / expired / cancelled booking). Retrying will not help; needs review. */
    REJECTED,
    /** Booking could not be reached; try again later. */
    RETRY
}
