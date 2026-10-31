package com.tickethub.paymentservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Published as an in-process Spring event after a payment succeeds.
 * When you add Kafka, create an @EventListener (or @TransactionalEventListener) that
 * forwards this to a KafkaTemplate - no change needed in PaymentServiceImpl.
 */
public record PaymentSuccessfulEvent(
        String paymentReference,
        Long bookingId,
        Long userId,
        BigDecimal amount,
        String currency,
        LocalDateTime paidAt
) {
}
