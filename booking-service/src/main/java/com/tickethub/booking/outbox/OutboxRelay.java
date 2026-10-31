package com.tickethub.booking.outbox;

import com.tickethub.booking.service.BookingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Re-sends notification messages that could not reach Kafka the first time (e.g. Kafka was restarting). */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final NotificationOutboxRepository repo;
    private final BookingEventPublisher publisher;

    public OutboxRelay(NotificationOutboxRepository repo, BookingEventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${booking.outbox-relay-ms:10000}")
    public void relay() {
        // rows younger than 15s are still being sent by the normal path
        List<NotificationOutbox> pending = repo.findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(
                NotificationOutbox.Status.PENDING, Instant.now().minusSeconds(15));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Re-sending {} pending notification(s) to Kafka", pending.size());
        for (NotificationOutbox row : pending) {
            publisher.trySend(row);
        }
    }
}
