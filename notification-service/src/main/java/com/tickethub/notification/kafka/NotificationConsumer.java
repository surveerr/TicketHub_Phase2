package com.tickethub.notification.kafka;

import com.tickethub.notification.event.NotificationType;
import com.tickethub.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Kafka entry point. Messages arrive as raw JSON strings and are parsed in the
 * service, so one bad message can never break the consumer.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final NotificationService service;

    public NotificationConsumer(NotificationService service) {
        this.service = service;
    }
    public static final String BOOKING_ID_HEADER = "X-Booking-Id";

    @KafkaListener(topics = "${kafka.topic.booking-confirmed}")
    public void onBookingConfirmed(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- booking-confirmed: {}", record.value());
            service.handle(NotificationType.BOOKING_CONFIRMED, record.value());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    @KafkaListener(topics = "${kafka.topic.booking-cancelled}")
    public void onBookingCancelled(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- booking-cancelled: {}", record.value());
            service.handle(NotificationType.BOOKING_CANCELLED, record.value());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    @KafkaListener(topics = "${kafka.topic.payment-failed}")
    public void onPaymentFailed(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- payment-failed: {}", record.value());
            service.handle(NotificationType.PAYMENT_FAILED, record.value());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    private void setupMdc(ConsumerRecord<String, String> record) {
        String correlationId = null;
        if (record.headers() != null) {
            Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
            if (header != null && header.value() != null) {
                correlationId = new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);

        String bookingId = null;
        if (record.headers() != null) {
            Header bHeader = record.headers().lastHeader(BOOKING_ID_HEADER);
            if (bHeader != null && bHeader.value() != null) {
                bookingId = new String(bHeader.value(), StandardCharsets.UTF_8);
            }
        }
        if ((bookingId == null || bookingId.isBlank()) && record.key() != null && !record.key().isBlank()) {
            bookingId = record.key();
        }
        if (bookingId != null && !bookingId.isBlank()) {
            MDC.put("bookingId", bookingId);
        }
    }
}
