package com.tickethub.paymentservice.service.impl;

import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.entity.PaymentStatus;
import com.tickethub.paymentservice.provider.PaymentProviderClient.OrderStatusResult;
import com.tickethub.paymentservice.repository.PaymentRepository;
import com.tickethub.paymentservice.service.PaymentProviderService;
import com.tickethub.paymentservice.service.PaymentReconciliationService;
import com.tickethub.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProviderService providerService;
    private final PaymentService paymentService;
    private final com.tickethub.paymentservice.client.BookingClient bookingClient;
    private final long thresholdMinutes;
    private final long expireMinutes;

    public PaymentReconciliationServiceImpl(
            PaymentRepository paymentRepository,
            PaymentProviderService providerService,
            PaymentService paymentService,
            com.tickethub.paymentservice.client.BookingClient bookingClient,
            @Value("${payment.reconciliation.pending-threshold-minutes:5}") long thresholdMinutes,
            @Value("${payment.reconciliation.expire-minutes:30}") long expireMinutes) {
        this.paymentRepository = paymentRepository;
        this.providerService = providerService;
        this.paymentService = paymentService;
        this.bookingClient = bookingClient;
        this.thresholdMinutes = thresholdMinutes;
        this.expireMinutes = expireMinutes;
    }

    @Override
    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms:60000}")
    public int reconcilePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> pending = paymentRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, now.minusMinutes(thresholdMinutes));
        LocalDateTime expiryCutoff = now.minusMinutes(expireMinutes);
        int updated = 0;

        for (Payment p : pending) {
            try {
                if (p.getProviderOrderId() == null) {
                    paymentService.applyStatusUpdate(p.getPaymentReference(), PaymentStatus.FAILED,
                            null, "No provider order was created");
                    updated++;
                    continue;
                }

                OrderStatusResult result = providerService.checkOrderStatus(p.getProviderOrderId());
                if ("PAID".equals(result.status())) {
                    paymentService.applyStatusUpdate(p.getPaymentReference(), PaymentStatus.SUCCESS,
                            result.paymentId(), null);
                    updated++;
                } else if (p.getCreatedAt().isBefore(expiryCutoff)) {
                    paymentService.applyStatusUpdate(p.getPaymentReference(), PaymentStatus.FAILED,
                            null, "Payment was not completed in time");
                    updated++;
                } else {
                    log.debug("Payment {} still pending", p.getPaymentReference());
                }
            } catch (Exception e) {
                log.error("Reconciliation failed for {}", p.getPaymentReference(), e);
            }
        }
        if (updated > 0) {
            log.info("Reconciliation updated {} payment(s)", updated);
        }
        retryUndeliveredBookingResults(now);
        return updated;
    }

    /**
     * Re-sends final results (SUCCESS/FAILED) that the Booking Service has not received yet, e.g. because it was down
     * when the payment completed. Results younger than 30 seconds are skipped: inline delivery is still in progress.
     */
    private void retryUndeliveredBookingResults(LocalDateTime now) {
        List<Payment> undelivered = paymentRepository.findByBookingSyncPendingTrueAndUpdatedAtBefore(now.minusSeconds(30));
        for (Payment p : undelivered) {
            try {
                paymentService.syncBookingResult(p.getPaymentReference());
            } catch (Exception e) {
                log.error("Re-sending result of {} to Booking failed", p.getPaymentReference(), e);
            }
        }
    }

    @Override
    public java.util.Map<String, Object> generateReconciliationAudit() {
        List<Payment> successPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .toList();

        List<java.util.Map<String, Object>> mismatches = new java.util.ArrayList<>();
        for (Payment p : successPayments) {
            try {
                com.tickethub.paymentservice.client.BookingDetails booking = bookingClient.getBooking(p.getBookingId());
                if (booking == null || !"CONFIRMED".equalsIgnoreCase(booking.status())) {
                    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("paymentReference", p.getPaymentReference());
                    item.put("bookingId", p.getBookingId());
                    item.put("paymentStatus", p.getStatus().name());
                    item.put("bookingStatus", booking != null ? booking.status() : "NOT_FOUND");
                    item.put("amount", p.getAmount());
                    item.put("actionRequired", "Captured payment with unconfirmed booking. Trigger refund or confirm booking.");
                    mismatches.add(item);
                }
            } catch (Exception e) {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("paymentReference", p.getPaymentReference());
                item.put("bookingId", p.getBookingId());
                item.put("paymentStatus", p.getStatus().name());
                item.put("error", e.getMessage());
                mismatches.add(item);
            }
        }

        java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("generatedAt", java.time.Instant.now().toString());
        report.put("totalSuccessPaymentsAudited", successPayments.size());
        report.put("mismatchesCount", mismatches.size());
        report.put("mismatches", mismatches);
        return report;
    }
}
