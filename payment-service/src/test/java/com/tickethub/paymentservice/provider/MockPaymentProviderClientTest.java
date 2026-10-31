package com.tickethub.paymentservice.provider;

import com.tickethub.paymentservice.config.ProviderConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentProviderClientTest {

    private final ProviderConfig config = new ProviderConfig();
    private final MockPaymentProviderClient client = new MockPaymentProviderClient(config);

    @Test
    void createsOrder() {
        String orderId = client.createOrder("PAY-1", new BigDecimal("100.00"), "INR");
        assertTrue(orderId.startsWith("order_mock_"));
    }

    @Test
    void errCurrencyFailsOrderCreation() {
        assertThrows(ProviderException.class,
                () -> client.createOrder("PAY-2", new BigDecimal("100.00"), "ERR"));
    }

    @Test
    void verifiesOnlyTheValidSignature() {
        assertTrue(client.verifyPayment("order_x", "pay_x", "valid_signature"));
        assertFalse(client.verifyPayment("order_x", "pay_x", "tampered"));
    }

    @Test
    void webhookSignatureMustMatchSecret() {
        config.setWebhookSecret("s3cret");
        assertTrue(client.verifyWebhook("{}", "s3cret"));
        assertFalse(client.verifyWebhook("{}", "wrong"));
        assertFalse(client.verifyWebhook("{}", null));
    }
}
