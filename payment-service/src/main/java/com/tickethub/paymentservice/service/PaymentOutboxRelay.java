package com.tickethub.paymentservice.service;

import com.tickethub.paymentservice.entity.PaymentOutbox;
import com.tickethub.paymentservice.repository.PaymentOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class PaymentOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxRelay.class);

    private final PaymentOutboxRepository outboxRepository;
    private final PaymentEventPublisher eventPublisher;

    public PaymentOutboxRelay(PaymentOutboxRepository outboxRepository, PaymentEventPublisher eventPublisher) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${payment.outbox-relay-ms:10000}")
    public void relay() {
        // rows older than 10s that are still PENDING are re-attempted
        List<PaymentOutbox> pending = outboxRepository.findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(
                PaymentOutbox.Status.PENDING, Instant.now().minusSeconds(10));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Relaying {} pending payment outbox event(s) to Kafka", pending.size());
        for (PaymentOutbox row : pending) {
            eventPublisher.trySend(row);
        }
    }
}
