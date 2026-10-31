package com.tickethub.paymentservice.provider;

import java.math.BigDecimal;

/**
 * Gateway abstraction. Exactly one implementation is active, chosen by payment.provider.name
 * (MOCK or RAZORPAY).
 */
public interface PaymentProviderClient {

    /** status is PAID or PENDING. */
    record OrderStatusResult(String status, String paymentId, String message) {
    }

    /** Creates an order at the provider and returns the provider's order id. */
    String createOrder(String paymentReference, BigDecimal amount, String currency);

    /** Checks the signature the browser got back from Checkout. */
    boolean verifyPayment(String orderId, String paymentId, String signature);

    /** Checks the signature header of a webhook against the raw request body. */
    boolean verifyWebhook(String rawBody, String signature);

    OrderStatusResult fetchOrderStatus(String orderId);

    /** Refunds a captured payment and returns the provider's refund id. */
    String refund(String paymentId, BigDecimal amount);

    /** Public key the frontend needs to open Checkout. */
    String getPublicKey();
}
