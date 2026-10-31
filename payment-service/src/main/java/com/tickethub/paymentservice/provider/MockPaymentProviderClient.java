package com.tickethub.paymentservice.provider;

import com.tickethub.paymentservice.config.ProviderConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fake gateway for local testing without Razorpay keys.
 *  - verifyPayment succeeds only when the signature is "valid_signature"
 *  - verifyWebhook succeeds only when the signature equals payment.provider.webhook-secret
 *  - currency "ERR" makes createOrder fail (to test failure handling / the circuit breaker)
 *  - payment.provider.mock-latency-ms delays createOrder (to test the provider timeout)
 *  - a payment FAILURE is simulated by sending the payment.failed webhook (see README)
 */
@Component
@ConditionalOnProperty(prefix = "payment.provider", name = "name", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentProviderClient implements PaymentProviderClient {

    private final ProviderConfig config;

    public MockPaymentProviderClient(ProviderConfig config) {
        this.config = config;
    }

    @Override
    public String createOrder(String paymentReference, BigDecimal amount, String currency) {
        if (config.getMockLatencyMs() > 0) {
            try {
                Thread.sleep(config.getMockLatencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderException("Mock provider call interrupted", e);
            }
        }
        if ("ERR".equalsIgnoreCase(currency)) {
            throw new ProviderException("Mock provider failure");
        }
        return "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        return "valid_signature".equals(signature);
    }

    @Override
    public boolean verifyWebhook(String rawBody, String signature) {
        return signature != null && signature.equals(config.getWebhookSecret());
    }

    @Override
    public OrderStatusResult fetchOrderStatus(String orderId) {
        return new OrderStatusResult("PENDING", null, "Mock: no payment found");
    }

    @Override
    public String refund(String paymentId, BigDecimal amount) {
        return "rfnd_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Override
    public String getPublicKey() {
        return "rzp_test_mock";
    }
}
