package com.tickethub.paymentservice.kafka;

import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.entity.PaymentInbox;
import com.tickethub.paymentservice.entity.PaymentStatus;
import com.tickethub.paymentservice.event.BookingCancelledEvent;
import com.tickethub.paymentservice.event.BookingRejectedEvent;
import com.tickethub.paymentservice.event.PaymentEventJson;
import com.tickethub.paymentservice.event.PaymentRefundedEvent;
import com.tickethub.paymentservice.repository.PaymentInboxRepository;
import com.tickethub.paymentservice.repository.PaymentRepository;
import com.tickethub.paymentservice.service.PaymentEventPublisher;
import com.tickethub.paymentservice.service.PaymentProviderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentCompensationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompensationConsumer.class);

    private final PaymentRepository paymentRepository;
    private final PaymentInboxRepository inboxRepository;
    private final PaymentProviderService providerService;
    private final PaymentEventPublisher eventPublisher;

    public PaymentCompensationConsumer(
            PaymentRepository paymentRepository,
            PaymentInboxRepository inboxRepository,
            PaymentProviderService providerService,
            PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.inboxRepository = inboxRepository;
        this.providerService = providerService;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "${kafka.topic.booking-rejected:tickethub.booking.rejected}",
            groupId = "payment-saga-compensation-group")
    public void onBookingRejected(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- booking-rejected event: {}", record.value());
            BookingRejectedEvent event = PaymentEventJson.parse(record.value(), BookingRejectedEvent.class);
            processCompensation(event.eventId(), "BookingRejected", event.bookingId(), event.reason(), event.aggregateVersion());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    @KafkaListener(topics = "${kafka.topic.booking-cancelled:tickethub.booking.cancelled}",
            groupId = "payment-saga-compensation-group")
    public void onBookingCancelled(ConsumerRecord<String, String> record) {
        setupMdc(record);
        try {
            log.info("Kafka <- booking-cancelled event: {}", record.value());
            BookingCancelledEvent event = PaymentEventJson.parse(record.value(), BookingCancelledEvent.class);
            processCompensation(event.eventId(), "BookingCancelled", event.bookingId(), event.reason(), event.aggregateVersion());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("bookingId");
        }
    }

    @Transactional
    public void processCompensation(String eventId, String eventType, Long bookingId, String reason, Long version) {
        if (eventId == null || eventId.isBlank()) {
            eventId = eventType + ":" + bookingId;
        }

        // Idempotent inbox guard: reject duplicate compensation events
        if (inboxRepository.existsByEventId(eventId)) {
            log.info("Saga compensation event {} already processed in inbox. Skipping duplicate.", eventId);
            return;
        }

        inboxRepository.save(new PaymentInbox(eventId, eventType, bookingId));

        Optional<Payment> optPayment = paymentRepository.findTopByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCESS);
        if (optPayment.isEmpty()) {
            log.warn("No SUCCESS payment found for booking {} to refund upon {}", bookingId, eventType);
            return;
        }

        Payment payment = optPayment.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment {} already refunded for booking {}.", payment.getPaymentReference(), bookingId);
            return;
        }

        log.info("Initiating saga compensation refund for booking {} (payment {}) reason: {}",
                bookingId, payment.getPaymentReference(), reason);

        try {
            providerService.refund(payment);
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setActiveBookingId(null);
            payment.setFailureReason("Compensated due to " + eventType + ": " + reason);
            paymentRepository.save(payment);

            PaymentRefundedEvent refundedEvent = new PaymentRefundedEvent(
                    UUID.randomUUID().toString(),
                    "PaymentRefunded",
                    bookingId,
                    payment.getPaymentReference(),
                    "REF-" + UUID.randomUUID(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    version != null ? version + 1 : 2L,
                    Instant.now()
            );

            eventPublisher.enqueuePaymentRefunded(refundedEvent);
            log.info("Saga compensation refund complete for booking {}. Enqueued PaymentRefunded event.", bookingId);
        } catch (Exception e) {
            log.error("Failed executing compensation refund for booking {}: {}", bookingId, e.getMessage(), e);
            throw new RuntimeException("Compensation refund failed", e);
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
