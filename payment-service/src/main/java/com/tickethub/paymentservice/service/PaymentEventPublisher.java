package com.tickethub.paymentservice.service;

import com.tickethub.paymentservice.entity.PaymentOutbox;
import com.tickethub.paymentservice.event.PaymentEventJson;
import com.tickethub.paymentservice.event.PaymentFailedEvent;
import com.tickethub.paymentservice.event.PaymentRefundedEvent;
import com.tickethub.paymentservice.event.PaymentSucceededEvent;
import com.tickethub.paymentservice.repository.PaymentOutboxRepository;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentOutboxRepository outboxRepository;
    private final String paymentSucceededTopic;
    private final String paymentFailedTopic;
    private final String paymentRefundedTopic;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public PaymentEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            PaymentOutboxRepository outboxRepository,
            @Value("${kafka.topic.payment-succeeded:tickethub.payment.succeeded}") String paymentSucceededTopic,
            @Value("${kafka.topic.payment-failed:tickethub.payment.failed}") String paymentFailedTopic,
            @Value("${kafka.topic.payment-refunded:tickethub.payment.refunded}") String paymentRefundedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRepository = outboxRepository;
        this.paymentSucceededTopic = paymentSucceededTopic;
        this.paymentFailedTopic = paymentFailedTopic;
        this.paymentRefundedTopic = paymentRefundedTopic;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public PaymentOutbox enqueuePaymentSucceeded(PaymentSucceededEvent event) {
        return enqueue(paymentSucceededTopic, event.eventId(), String.valueOf(event.bookingId()), "PaymentSucceeded", event);
    }

    public PaymentOutbox enqueuePaymentFailed(PaymentFailedEvent event) {
        return enqueue(paymentFailedTopic, event.eventId(), String.valueOf(event.bookingId()), "PaymentFailed", event);
    }

    public PaymentOutbox enqueuePaymentRefunded(PaymentRefundedEvent event) {
        return enqueue(paymentRefundedTopic, event.eventId(), String.valueOf(event.bookingId()), "PaymentRefunded", event);
    }

    private PaymentOutbox enqueue(String topic, String eventId, String key, String eventType, Object event) {
        PaymentOutbox outbox = new PaymentOutbox();
        outbox.setEventId(eventId != null ? eventId : UUID.randomUUID().toString());
        outbox.setTopic(topic);
        outbox.setMessageKey(key);
        outbox.setEventType(eventType);
        outbox.setPayload(PaymentEventJson.write(event));
        outbox.setStatus(PaymentOutbox.Status.PENDING);
        outbox = outboxRepository.save(outbox);

        final PaymentOutbox saved = outbox;
        executor.submit(() -> trySend(saved));
        return saved;
    }

    public boolean trySend(PaymentOutbox row) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(row.getTopic(), row.getMessageKey(), row.getPayload());

            String corrId = MDC.get("correlationId");
            if (corrId != null && !corrId.isBlank()) {
                record.headers().add("X-Correlation-Id", corrId.getBytes(StandardCharsets.UTF_8));
            }
            if (row.getMessageKey() != null && !row.getMessageKey().isBlank()) {
                record.headers().add("X-Booking-Id", row.getMessageKey().getBytes(StandardCharsets.UTF_8));
            }

            kafkaTemplate.send(record).get(8, TimeUnit.SECONDS);
            row.setStatus(PaymentOutbox.Status.SENT);
            row.setSentAt(Instant.now());
            row.setAttempts(row.getAttempts() + 1);
            outboxRepository.save(row);
            log.info("Payment outbox event {} ({}) published to Kafka topic {} for key {}",
                    row.getEventId(), row.getEventType(), row.getTopic(), row.getMessageKey());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            row.setAttempts(row.getAttempts() + 1);
            outboxRepository.save(row);
            log.warn("Failed sending outbox row {} to Kafka: {}. Outbox relay will retry.", row.getId(), e.getMessage());
            return false;
        }
    }
}
