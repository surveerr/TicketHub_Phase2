package com.tickethub.paymentservice.service;

import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.provider.PaymentProviderClient.OrderStatusResult;

public interface PaymentProviderService {

    /** @return provider order id; throws PaymentFailedException if the provider is unavailable */
    String createOrder(Payment payment);

    boolean verifyPayment(String orderId, String paymentId, String signature);

    boolean verifyWebhook(String rawBody, String signature);

    OrderStatusResult checkOrderStatus(String orderId);

    /** @return provider refund id; throws PaymentFailedException if the refund could not be made */
    String refund(Payment payment);

    String getPublicKey();
}
