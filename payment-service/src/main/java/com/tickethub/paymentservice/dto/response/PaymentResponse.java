package com.tickethub.paymentservice.dto.response;

import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.entity.PaymentStatus;
import com.tickethub.paymentservice.provider.PaymentProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * providerOrderId + checkoutKey are what the frontend passes to Razorpay Checkout.
 * checkoutKey is the PUBLIC key id (never the secret).
 */
public record PaymentResponse(
        String paymentReference,
        Long bookingId,
        Long userId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        PaymentProvider provider,
        String providerOrderId,
        String providerTransactionId,
        String checkoutKey,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentResponse from(Payment p, String checkoutKey) {
        return new PaymentResponse(
                p.getPaymentReference(),
                p.getBookingId(),
                p.getUserId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getProvider(),
                p.getProviderOrderId(),
                p.getProviderTransactionId(),
                checkoutKey,
                p.getFailureReason(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
