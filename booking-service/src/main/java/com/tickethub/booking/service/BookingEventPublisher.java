package com.tickethub.booking.service;

import com.tickethub.booking.event.BookingConfirmedEvent;
import com.tickethub.booking.outbox.NotificationOutbox;
import com.tickethub.booking.outbox.NotificationOutboxRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Publishes notification events to Kafka - the Notification Service then e-mails the customer automatically.
 *
 *   confirmed      -> "Booking confirmed" mail      (payment succeeded)
 *   payment-failed -> "Payment failed" mail         (payment failed / could not be confirmed)
 *   cancelled      -> "Booking cancelled" mail      (customer cancelled or hold expired)
 *
 * Safety: the message is saved in the notification_outbox table first, then sent in the background.
 * If Kafka is down, OutboxRelay keeps retrying, so the booking/payment API never fails or waits because of mail.
 * The message is a plain JSON String (no serializer / Jackson dependency).
 */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final NotificationOutboxRepository outbox;
    private final String confirmedTopic;
    private final String paymentFailedTopic;
    private final String cancelledTopic;
    private final String bookingRejectedTopic;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public BookingEventPublisher(KafkaTemplate<String, Object> kafka,
                                 NotificationOutboxRepository outbox,
                                 @Value("${kafka.topic.booking-confirmed:tickethub.booking.confirmed}") String confirmedTopic,
                                 @Value("${kafka.topic.payment-failed:tickethub.payment.failed}") String paymentFailedTopic,
                                 @Value("${kafka.topic.booking-cancelled:tickethub.booking.cancelled}") String cancelledTopic,
                                 @Value("${kafka.topic.booking-rejected:tickethub.booking.rejected}") String bookingRejectedTopic) {
        this.kafka = kafka;
        this.outbox = outbox;
        this.confirmedTopic = confirmedTopic;
        this.paymentFailedTopic = paymentFailedTopic;
        this.cancelledTopic = cancelledTopic;
        this.bookingRejectedTopic = bookingRejectedTopic;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Kept for existing callers: same as publishConfirmed. */
    public void publish(BookingConfirmedEvent event) {
        publishConfirmed(event);
    }

    public void publishConfirmed(BookingConfirmedEvent event) {
        enqueue(confirmedTopic, event);
    }

    public void publishPaymentFailed(BookingConfirmedEvent event) {
        enqueue(paymentFailedTopic, event);
    }

    public void publishCancelled(BookingConfirmedEvent event) {
        enqueue(cancelledTopic, event);
    }

    public void publishBookingRejected(com.tickethub.booking.event.BookingRejectedEvent event) {
        enqueueGeneric(bookingRejectedTopic, event.eventId(), String.valueOf(event.bookingId()), "BookingRejected",
                com.tickethub.booking.event.BookingEventJson.write(event));
    }

    public void publishBookingCancelled(com.tickethub.booking.event.BookingCancelledEvent event) {
        enqueueGeneric(cancelledTopic, event.eventId(), String.valueOf(event.bookingId()), "BookingCancelled",
                com.tickethub.booking.event.BookingEventJson.write(event));
    }

    public void enqueueGeneric(String topic, String eventId, String key, String eventType, String payload) {
        try {
            NotificationOutbox row = new NotificationOutbox();
            row.setEventId(eventId);
            row.setEventType(eventType);
            row.setTopic(topic);
            row.setMessageKey(key);
            row.setPayload(payload);
            row = outbox.save(row);

            final NotificationOutbox saved = row;
            executor.submit(() -> trySend(saved));
        } catch (Exception e) {
            log.error("Could not queue outbox row for key {} on {}", key, topic, e);
        }
    }

    private void enqueue(String topic, BookingConfirmedEvent event) {
        try {
            if (event.recipientEmail() == null || event.recipientEmail().isBlank()) {
                log.warn("No customer e-mail for booking {} - the login token has no e-mail claim, "
                        + "so NO notification is sent to topic {}", event.bookingId(), topic);
                return;
            }
            NotificationOutbox row = new NotificationOutbox();
            row.setEventId(event.eventId());
            row.setEventType(event.eventType() != null ? event.eventType() : "BookingConfirmed");
            row.setTopic(topic);
            row.setMessageKey(String.valueOf(event.bookingId()));
            row.setPayload(toJson(event));
            row = outbox.save(row);

            final NotificationOutbox saved = row;
            executor.submit(() -> trySend(saved));
        } catch (Exception e) {
            log.error("Could not queue notification for booking {} on {}", event.bookingId(), topic, e);
        }
    }

    /** Sends one outbox row to Kafka. Used right after saving and by OutboxRelay for leftovers. */
    public boolean trySend(NotificationOutbox row) {
        try {
            org.apache.kafka.clients.producer.ProducerRecord<String, Object> record =
                    new org.apache.kafka.clients.producer.ProducerRecord<>(row.getTopic(), row.getMessageKey(), row.getPayload());
            String corrId = org.slf4j.MDC.get("correlationId");
            if (corrId != null && !corrId.isBlank()) {
                record.headers().add("X-Correlation-Id", corrId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (row.getMessageKey() != null && !row.getMessageKey().isBlank()) {
                org.slf4j.MDC.put("bookingId", row.getMessageKey());
                record.headers().add("X-Booking-Id", row.getMessageKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            kafka.send(record).get(8, TimeUnit.SECONDS);
            row.setStatus(NotificationOutbox.Status.SENT);
            row.setSentAt(Instant.now());
            row.setAttempts(row.getAttempts() + 1);
            outbox.save(row);
            log.info("Notification published to Kafka topic {} for booking {}", row.getTopic(), row.getMessageKey());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            try {
                row.setAttempts(row.getAttempts() + 1);
                outbox.save(row);
            } catch (Exception ignored) {
                // row stays PENDING and is picked up again by OutboxRelay
            }
            log.warn("Kafka publish to {} failed for booking {} ({}). Will retry automatically.",
                    row.getTopic(), row.getMessageKey(), e.toString());
            return false;
        }
    }

    static String toJson(BookingConfirmedEvent e) {
        StringBuilder sb = new StringBuilder(320);
        sb.append('{');
        sb.append("\"eventId\":").append(str(e.eventId())).append(',');
        sb.append("\"bookingId\":").append(e.bookingId()).append(',');
        sb.append("\"customerId\":").append(e.customerId()).append(',');
        sb.append("\"recipientEmail\":").append(str(e.recipientEmail())).append(',');
        sb.append("\"showId\":").append(e.showId()).append(',');
        sb.append("\"seatIds\":").append(ids(e.seatIds())).append(',');
        sb.append("\"amount\":").append(e.amount() == null ? "null" : e.amount().toPlainString()).append(',');
        sb.append("\"currency\":").append(str(e.currency())).append(',');
        sb.append("\"timestamp\":").append(str(e.timestamp() == null ? null : e.timestamp().toString())).append(',');
        sb.append("\"showTime\":").append(str(e.showTime())).append(',');
        sb.append("\"paymentId\":").append(str(e.paymentId())).append(',');
        sb.append("\"reason\":").append(str(e.reason()));
        sb.append('}');
        return sb.toString();
    }

    private static String ids(List<Long> ids) {
        if (ids == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.append(']').toString();
    }

    private static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
