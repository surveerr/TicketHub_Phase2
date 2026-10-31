package com.tickethub.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/** The three values Razorpay Checkout returns to the browser after a successful payment. */
public record VerifyPaymentRequest(
        @NotBlank String paymentReference,
        @NotBlank String razorpayOrderId,
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature
) {
}
