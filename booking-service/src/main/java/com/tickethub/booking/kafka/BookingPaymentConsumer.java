package com.tickethub.booking.kafka;

import com.tickethub.booking.event.BookingEventJson;
import com.tickethub.booking.event.PaymentFailedEvent;
import com.tickethub.booking.event.PaymentRefundedEvent;
import com.tickethub.booking.event.PaymentSucceededEvent;
import com.tickethub.booking.service.BookingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class BookingPaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingPaymentConsumer.class);

    private final BookingService bookingService;

    public BookingPaymentConsumer(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @KafkaListener(topics = "${kafka.topic.payment-succeeded:tickethub.payment.succeeded}",
            groupId = "booking-saga-orchestration-group")
    public void onPaymentSucceeded(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- payment-succeeded event: {}", record.value());
            PaymentSucceededEvent event = BookingEventJson.parse(record.value(), PaymentSucceededEvent.class);
            bookingService.processPaymentSucceeded(event);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    @KafkaListener(topics = "${kafka.topic.payment-failed:tickethub.payment.failed}",
            groupId = "booking-saga-orchestration-group")
    public void onPaymentFailed(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- payment-failed event: {}", record.value());
            PaymentFailedEvent event = BookingEventJson.parse(record.value(), PaymentFailedEvent.class);
            bookingService.processPaymentFailed(event);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    @KafkaListener(topics = "${kafka.topic.payment-refunded:tickethub.payment.refunded}",
            groupId = "booking-saga-orchestration-group")
    public void onPaymentRefunded(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- payment-refunded event: {}", record.value());
            PaymentRefundedEvent event = BookingEventJson.parse(record.value(), PaymentRefundedEvent.class);
            bookingService.processPaymentRefunded(event);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    private void setupMdc(ConsumerRecord<String, String> record) {
        String corrId = null;
        if (record.headers() != null) {
            Header header = record.headers().lastHeader("X-Correlation-Id");
            if (header != null && header.value() != null) {
                corrId = new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        if (corrId == null || corrId.isBlank()) {
            corrId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", corrId);

        if (record.key() != null && !record.key().isBlank()) {
            MDC.put("bookingId", record.key());
        }
    }
}
