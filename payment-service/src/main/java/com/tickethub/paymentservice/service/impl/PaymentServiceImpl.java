package com.tickethub.paymentservice.service.impl;

import com.tickethub.paymentservice.client.BookingClient;
import com.tickethub.paymentservice.client.BookingDetails;
import com.tickethub.paymentservice.client.BookingPaymentResult;
import com.tickethub.paymentservice.client.BookingSyncResult;
import com.tickethub.paymentservice.config.ProviderConfig;
import com.tickethub.paymentservice.dto.request.CreatePaymentRequest;
import com.tickethub.paymentservice.dto.request.VerifyPaymentRequest;
import com.tickethub.paymentservice.dto.response.PaymentResponse;
import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.entity.PaymentAttempt;
import com.tickethub.paymentservice.entity.PaymentStatus;
import com.tickethub.paymentservice.event.PaymentSuccessfulEvent;
import com.tickethub.paymentservice.exception.BookingNotPayableException;
import com.tickethub.paymentservice.exception.PaymentFailedException;
import com.tickethub.paymentservice.exception.PaymentNotFoundException;
import com.tickethub.paymentservice.exception.ProviderUnavailableException;
import com.tickethub.paymentservice.repository.PaymentAttemptRepository;
import com.tickethub.paymentservice.repository.PaymentRepository;
import com.tickethub.paymentservice.event.PaymentFailedEvent;
import com.tickethub.paymentservice.event.PaymentSucceededEvent;
import com.tickethub.paymentservice.service.PaymentEventPublisher;
import com.tickethub.paymentservice.service.PaymentProviderService;
import com.tickethub.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Flow: createPayment (read booking -> PENDING + provider order) -> browser pays in Checkout ->
 * verifyPayment (signature) and/or webhook -> SUCCESS -> Booking Service is told the result.
 * Reconciliation settles anything missed and re-delivers results Booking has not received yet.
 *
 * Methods are deliberately not @Transactional as a whole so the PENDING row is committed before
 * any provider call; status changes go through applyStatusUpdate, which uses a row lock.
 *
 * Payment initiation is never retried automatically (a retry could create a second order); the client
 * retries with the same idempotency key / booking id and gets the existing payment back.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /** Prefix of attempt records written for a provider "payment.failed" event (used to de-duplicate webhooks). */
    private static final String FAILED_EVENT_PREFIX = "payment.failed ";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentProviderService providerService;
    private final ProviderConfig providerConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final BookingClient bookingClient;
    private final PaymentEventPublisher paymentOutboxPublisher;
    private final int maxFailedAttempts;
    private final String defaultCurrency;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentAttemptRepository attemptRepository,
                              PaymentProviderService providerService,
                              ProviderConfig providerConfig,
                              ApplicationEventPublisher eventPublisher,
                              TransactionTemplate transactionTemplate,
                              BookingClient bookingClient,
                              PaymentEventPublisher paymentOutboxPublisher,
                              @Value("${payment.max-failed-attempts:1}") int maxFailedAttempts,
                              @Value("${payment.default-currency:INR}") String defaultCurrency) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.providerService = providerService;
        this.providerConfig = providerConfig;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.bookingClient = bookingClient;
        this.paymentOutboxPublisher = paymentOutboxPublisher;
        this.maxFailedAttempts = Math.max(1, maxFailedAttempts);
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request, Long customerId) {
        if (request != null && request.bookingId() != null) {
            org.slf4j.MDC.put("bookingId", String.valueOf(request.bookingId()));
        }
        String key = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? null : request.idempotencyKey();

        if (key != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                if (!java.util.Objects.equals(customerId, existing.get().getUserId())) throw new PaymentNotFoundException("Payment not found");
                if (!retireIfProviderChanged(existing.get())) {
                    log.info("Idempotent replay for key {} (bookingId={})", key, existing.get().getBookingId());
                    return toResponse(existing.get());
                }
            }
        }

        // The booking is the source of truth for owner, amount and currency - never the client.
        BookingDetails booking = bookingClient.getBooking(request.bookingId());
        assertPayable(request.bookingId(), booking);
        if (!java.util.Objects.equals(customerId, booking.customerId())) throw new BookingNotPayableException("You are not allowed to pay for this booking");

        // One live payment per booking: a repeated initiation returns the payment that already exists.
        Optional<Payment> live = paymentRepository.findByActiveBookingId(request.bookingId());
        if (live.isPresent() && retireIfProviderChanged(live.get())) {
            live = Optional.empty();
        }
        if (live.isPresent()) {
            log.info("Booking {} already has live payment {}, returning it", request.bookingId(),
                    live.get().getPaymentReference());
            return toResponse(live.get());
        }

        String currency = booking.currency() == null || booking.currency().isBlank()
                ? defaultCurrency : booking.currency();

        Payment payment = new Payment();
        payment.setPaymentReference("PAY-" + UUID.randomUUID());   // 40 chars = Razorpay receipt limit
        payment.setBookingId(request.bookingId());
        payment.setUserId(booking.customerId());
        payment.setAmount(booking.amount());
        payment.setCurrency(currency.toUpperCase(Locale.ROOT));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(providerConfig.getName());
        payment.setIdempotencyKey(key);
        payment.setActiveBookingId(request.bookingId());
        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent request for the same key or booking: return the winner.
            Optional<Payment> winner = key != null ? paymentRepository.findByIdempotencyKey(key) : Optional.empty();
            if (winner.isEmpty()) {
                winner = paymentRepository.findByActiveBookingId(request.bookingId());
            }
            if (winner.isPresent()) {
                log.info("Concurrent payment request for booking {}, returning {}", request.bookingId(),
                        winner.get().getPaymentReference());
                return toResponse(winner.get());
            }
            throw e;
        }

        String orderId;
        try {
            orderId = providerService.createOrder(payment);
        } catch (RuntimeException e) {
            log.error("Order creation failed for {} (bookingId={}): {}", payment.getPaymentReference(),
                    payment.getBookingId(), e.toString());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Could not create order with the payment provider");
            payment.setActiveBookingId(null);           // frees the slot so the customer can try again
            paymentRepository.save(payment);
            throw new ProviderUnavailableException("Unable to start the payment with the provider, please try again");
        }

        payment.setProviderOrderId(orderId);
        payment = paymentRepository.save(payment);
        log.info("Payment {} created for booking {} amount {} {}", payment.getPaymentReference(),
                payment.getBookingId(), payment.getAmount(), payment.getCurrency());
        return toResponse(payment);
    }

    @Override
    public PaymentResponse verifyPayment(VerifyPaymentRequest request, Long customerId) {
        Payment payment = findByReference(request.paymentReference());
        if (payment.getBookingId() != null) {
            org.slf4j.MDC.put("bookingId", String.valueOf(payment.getBookingId()));
        }
        if (!java.util.Objects.equals(customerId, payment.getUserId())) throw new PaymentNotFoundException("Payment not found: " + request.paymentReference());

        if (payment.getProviderOrderId() == null
                || !payment.getProviderOrderId().equals(request.razorpayOrderId())) {
            throw new IllegalArgumentException("Order id does not match this payment");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            return toResponse(payment);     // already settled, nothing to do
        }

        if (payment.getProvider() != providerConfig.getName()) {
            throw new IllegalArgumentException("This payment was created with the " + payment.getProvider()
                    + " provider but the service now runs with " + providerConfig.getName()
                    + ". Call POST /api/payments again to create a new Razorpay order.");
        }

        boolean valid = providerService.verifyPayment(
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());

        if (!valid) {
            recordAttempt(payment, PaymentStatus.FAILED, "Signature verification failed");
            throw new PaymentFailedException("Invalid payment signature. razorpayOrderId, razorpayPaymentId and razorpaySignature must be the values returned by Razorpay Checkout for this order; hand-typed or mock values are rejected");
        }

        recordAttempt(payment, PaymentStatus.SUCCESS, "Signature verified for " + request.razorpayPaymentId());
        applyStatusUpdate(payment.getPaymentReference(), PaymentStatus.SUCCESS,
                request.razorpayPaymentId(), null);
        return toResponse(findByReference(payment.getPaymentReference()));
    }

    @Override
    public PaymentResponse getPayment(String paymentReference, Long customerId) {
        PaymentResponse p = toResponse(findByReference(paymentReference));
        if (!java.util.Objects.equals(customerId, p.userId())) throw new PaymentNotFoundException("Payment not found: " + paymentReference);
        return p;
    }

    @Override
    public List<PaymentResponse> getPaymentsByBooking(Long bookingId, Long customerId) {
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream().filter(p -> java.util.Objects.equals(customerId,p.getUserId())).map(this::toResponse).toList();
    }

    @Override
    public PaymentResponse refundPayment(String paymentReference, Long customerId) {
        Payment payment = findByReference(paymentReference);
        if (!java.util.Objects.equals(customerId, payment.getUserId())) throw new PaymentNotFoundException("Payment not found: " + paymentReference);
        if (payment.getStatus() != PaymentStatus.SUCCESS || payment.getProviderTransactionId() == null) {
            throw new PaymentFailedException("Only SUCCESS payments can be refunded, current status: "
                    + payment.getStatus());
        }
        providerService.refund(payment);    // throws PaymentFailedException on failure
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setActiveBookingId(null);
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    public void handleProviderEvent(String eventType, String orderId, String paymentId, String failureReason) {
        if (orderId == null || orderId.isBlank()) {
            log.warn("Webhook {} without an order id, ignoring", eventType);
            return;
        }
        Optional<Payment> found = paymentRepository.findByProviderOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("Webhook {} for unknown order {}, ignoring", eventType, orderId);
            return;                         // answer 200 so the provider does not keep retrying
        }
        Payment payment = found.get();

        switch (eventType) {
            case "payment.captured", "order.paid" ->
                    applyStatusUpdate(payment.getPaymentReference(), PaymentStatus.SUCCESS, paymentId, null);
            case "payment.failed" -> handlePaymentFailedEvent(payment, paymentId, failureReason);
            default -> log.debug("Ignoring webhook event {}", eventType);
        }
    }

    /**
     * A failed attempt is recorded once per provider payment id (a re-delivered webhook is ignored). The payment
     * becomes FAILED - and Booking is told so seats are released - once payment.max-failed-attempts is reached;
     * with the default of 1 the first failure is final. Raise it to let the customer retry on the same order.
     */
    private void handlePaymentFailedEvent(Payment payment, String paymentId, String failureReason) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Ignoring payment.failed for {}: already {}", payment.getPaymentReference(), payment.getStatus());
            return;
        }
        String reason = failureReason != null ? failureReason : "Payment attempt failed";
        if (paymentId != null) {
            String marker = FAILED_EVENT_PREFIX + paymentId + ":";
            if (attemptRepository.existsByPaymentIdAndProviderResponseStartingWith(payment.getId(), marker)) {
                log.info("Duplicate payment.failed webhook for {} ({}), ignoring", payment.getPaymentReference(), paymentId);
                return;
            }
            recordAttempt(payment, PaymentStatus.FAILED, marker + " " + reason);
        } else {
            recordAttempt(payment, PaymentStatus.FAILED, FAILED_EVENT_PREFIX + ": " + reason);
        }

        long failedAttempts = attemptRepository.countByPaymentIdAndStatusAndProviderResponseStartingWith(
                payment.getId(), PaymentStatus.FAILED, FAILED_EVENT_PREFIX);
        if (failedAttempts >= maxFailedAttempts) {
            applyStatusUpdate(payment.getPaymentReference(), PaymentStatus.FAILED, null, reason);
        }
    }

    @Override
    public void applyStatusUpdate(String paymentReference, PaymentStatus newStatus,
                                  String providerTransactionId, String failureReason) {
        Payment updated = transactionTemplate.execute(tx -> {
            Payment p = paymentRepository.findByPaymentReferenceForUpdate(paymentReference)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentReference));
            PaymentStatus current = p.getStatus();

            if (current == newStatus || current == PaymentStatus.REFUNDED) {
                log.info("Ignoring update for {}: already {}", paymentReference, current);
                return null;
            }
            if (newStatus == PaymentStatus.FAILED && current != PaymentStatus.PENDING) {
                log.info("Ignoring FAILED update for {}: currently {}", paymentReference, current);
                return null;
            }
            if (newStatus == PaymentStatus.SUCCESS && current == PaymentStatus.FAILED) {
                log.warn("Late success for {}: it had been marked FAILED but the provider captured it",
                        paymentReference);
            }

            p.setStatus(newStatus);
            if (providerTransactionId != null) {
                p.setProviderTransactionId(providerTransactionId);
            }
            if (newStatus == PaymentStatus.FAILED) {
                p.setFailureReason(failureReason != null ? failureReason : "Payment failed");
                p.setActiveBookingId(null);
                // A payment that never got a provider order never reached the customer, so Booking is not told.
                p.setBookingSyncPending(p.getProviderOrderId() != null);
            } else if (newStatus == PaymentStatus.SUCCESS) {
                p.setFailureReason(null);
                if (p.getActiveBookingId() == null) {       // late success on a payment that had been FAILED
                    if (paymentRepository.existsByActiveBookingId(p.getBookingId())) {
                        log.error("Payment {} succeeded late but booking {} already has another live payment - "
                                + "one of them needs a refund", paymentReference, p.getBookingId());
                    } else {
                        p.setActiveBookingId(p.getBookingId());
                    }
                }
                p.setBookingSyncPending(true);              // committed together with the status change
            }
            return paymentRepository.save(p);
        });

        if (updated != null) {
            if (newStatus == PaymentStatus.SUCCESS) {
                PaymentSucceededEvent succeededEvent = new PaymentSucceededEvent(
                        UUID.randomUUID().toString(),
                        "PaymentSucceeded",
                        updated.getBookingId(),
                        updated.getPaymentReference(),
                        updated.getAmount(),
                        updated.getCurrency(),
                        updated.getProviderTransactionId(),
                        1L,
                        java.time.Instant.now()
                );
                paymentOutboxPublisher.enqueuePaymentSucceeded(succeededEvent);
                publishSuccess(updated);        // after the transaction has committed
            } else if (newStatus == PaymentStatus.FAILED) {
                PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                        UUID.randomUUID().toString(),
                        "PaymentFailed",
                        updated.getBookingId(),
                        updated.getPaymentReference(),
                        updated.getFailureReason(),
                        1L,
                        java.time.Instant.now()
                );
                paymentOutboxPublisher.enqueuePaymentFailed(failedEvent);
            }
            if (updated.isBookingSyncPending()) {
                syncBookingResult(updated.getPaymentReference());
            }
        }
    }

    @Override
    public boolean syncBookingResult(String paymentReference) {
        Payment p = findByReference(paymentReference);
        if (!p.isBookingSyncPending()) {
            return true;
        }

        BookingPaymentResult result = switch (p.getStatus()) {
            case SUCCESS -> new BookingPaymentResult(p.getPaymentReference(), "SUCCESS",
                    p.getProviderTransactionId(), null);
            case FAILED -> new BookingPaymentResult(p.getPaymentReference(), "FAILED",
                    null, p.getFailureReason());
            default -> null;
        };
        if (result == null) {
            log.info("Payment {} is {}, no booking result to deliver", paymentReference, p.getStatus());
            markBookingSynced(paymentReference, null);
            return true;
        }

        BookingSyncResult outcome;
        try {
            outcome = bookingClient.notifyPaymentResult(p.getBookingId(), result);
        } catch (RuntimeException e) {
            log.warn("Booking notification for {} threw {}", paymentReference, e.toString());
            outcome = BookingSyncResult.RETRY;
        }

        switch (outcome) {
            case DELIVERED -> {
                log.info("Booking {} told payment {} is {}", p.getBookingId(), paymentReference, result.status());
                markBookingSynced(paymentReference, null);
                return true;
            }
            case REJECTED -> {
                log.error("Booking {} refused payment {} ({}); the booking was probably expired or cancelled - "
                        + "review this payment for a refund", p.getBookingId(), paymentReference, result.status());
                markBookingSynced(paymentReference,
                        "Booking Service refused the payment result (booking expired or cancelled?) - review for refund");
                return true;
            }
            default -> {
                log.warn("Booking {} not reachable, payment {} result will be re-sent", p.getBookingId(), paymentReference);
                return false;
            }
        }
    }

    // ---- helpers ----

    private void assertPayable(Long bookingId, BookingDetails booking) {
        if (!BookingDetails.PENDING_PAYMENT.equals(booking.status())) {
            throw new BookingNotPayableException("Booking " + bookingId + " is " + booking.status()
                    + ", only PENDING_PAYMENT bookings can be paid");
        }
        if (booking.expiresAt() != null && booking.expiresAt().isBefore(java.time.Instant.now())) {
            throw new BookingNotPayableException("The reservation for booking " + bookingId + " has expired");
        }
        if (booking.customerId() == null) {
            throw new BookingNotPayableException("Booking " + bookingId + " has no customer");
        }
        if (booking.amount() == null || booking.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingNotPayableException("Booking " + bookingId + " has no payable amount");
        }
    }

    private void markBookingSynced(String paymentReference, String note) {
        transactionTemplate.executeWithoutResult(tx ->
                paymentRepository.findByPaymentReferenceForUpdate(paymentReference).ifPresent(p -> {
                    p.setBookingSyncPending(false);
                    if (note != null) {
                        p.setFailureReason(note);
                    }
                    paymentRepository.save(p);
                }));
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.from(p, providerService.getPublicKey());
    }

    private Payment findByReference(String reference) {
        return paymentRepository.findByPaymentReference(reference)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + reference));
    }

    /**
     * A PENDING payment whose order was created by a different provider than the one now configured (typically a
     * MOCK order left over before switching to RAZORPAY) can never be paid: Razorpay Checkout cannot open a mock
     * order. Retire it so a fresh Razorpay order is created. Booking is NOT notified (the booking is still payable).
     * @return true if the payment was retired
     */
    private boolean retireIfProviderChanged(Payment p) {
        if (p.getStatus() != PaymentStatus.PENDING || p.getProvider() == providerConfig.getName()) return false;
        log.warn("Retiring PENDING payment {} created with provider {} (configured provider is now {})",
                p.getPaymentReference(), p.getProvider(), providerConfig.getName());
        p.setStatus(PaymentStatus.FAILED);
        p.setFailureReason("Superseded: created with " + p.getProvider() + " but provider is now " + providerConfig.getName());
        p.setActiveBookingId(null);
        p.setIdempotencyKey(null);
        paymentRepository.save(p);
        return true;
    }

    private void recordAttempt(Payment payment, PaymentStatus status, String message) {
        int next = (int) attemptRepository.countByPaymentId(payment.getId()) + 1;
        attemptRepository.save(new PaymentAttempt(payment, next, status, message));
    }

    private void publishSuccess(Payment p) {
        eventPublisher.publishEvent(new PaymentSuccessfulEvent(
                p.getPaymentReference(), p.getBookingId(), p.getUserId(),
                p.getAmount(), p.getCurrency(), LocalDateTime.now()));
    }
}
