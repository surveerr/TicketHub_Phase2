package com.tickethub.paymentservice.service;

import com.tickethub.paymentservice.dto.request.CreatePaymentRequest;
import com.tickethub.paymentservice.dto.request.VerifyPaymentRequest;
import com.tickethub.paymentservice.dto.response.PaymentResponse;
import com.tickethub.paymentservice.entity.PaymentStatus;

import java.util.List;

public interface PaymentService {

    /**
     * Creates a PENDING payment and a provider order; the browser then completes payment in Checkout.
     * Owner, amount and currency come from the Booking Service, never from the client. A booking has at most one live
     * payment: repeating the call returns the existing one.
     */
    PaymentResponse createPayment(CreatePaymentRequest request, Long customerId);

    /** Verifies the Checkout signature and marks the payment SUCCESS. */
    PaymentResponse verifyPayment(VerifyPaymentRequest request, Long customerId);

    PaymentResponse getPayment(String paymentReference, Long customerId);

    List<PaymentResponse> getPaymentsByBooking(Long bookingId, Long customerId);

    PaymentResponse refundPayment(String paymentReference, Long customerId);

    /** Handles an already signature-verified webhook event (payment.captured, order.paid, payment.failed). */
    void handleProviderEvent(String eventType, String orderId, String paymentId, String failureReason);

    /** Moves a payment to a new status (rules in the implementation). Safe to call repeatedly. */
    void applyStatusUpdate(String paymentReference, PaymentStatus newStatus,
                           String providerTransactionId, String failureReason);

    /**
     * Delivers a final result (SUCCESS/FAILED) to the Booking Service if it is still owed.
     * Safe to call repeatedly.
     *
     * @return true when nothing more is owed (delivered, refused by Booking, or nothing to deliver); false to retry later
     */
    boolean syncBookingResult(String paymentReference);
}
