package com.tickethub.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentResultRequest(@NotNull PaymentResultStatus status, @NotBlank String paymentReference, String providerTransactionId, String failureReason) {
    public enum PaymentResultStatus { SUCCESS, FAILED }
}
