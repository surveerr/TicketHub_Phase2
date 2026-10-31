package com.tickethub.paymentservice.provider;

import com.tickethub.paymentservice.config.ProviderConfig;
import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.service.impl.PaymentProviderServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ST-16 / SC-05: a slow provider ends in a controlled error instead of a hung request. */
class PaymentProviderTimeoutTest {

    @Test
    void slowProviderTriggersTimeout() {
        ProviderConfig config = new ProviderConfig();
        config.setTimeoutMs(150);
        config.setMockLatencyMs(2000);
        PaymentProviderServiceImpl service =
                new PaymentProviderServiceImpl(new MockPaymentProviderClient(config), config);

        Payment payment = new Payment();
        payment.setPaymentReference("PAY-slow");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("INR");

        long start = System.currentTimeMillis();
        ProviderException e = assertThrows(ProviderException.class, () -> service.createOrder(payment));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(e.getMessage().contains("timed out"));
        assertTrue(elapsed < 1500, "should fail fast, took " + elapsed + " ms");
        service.shutdown();
    }

    @Test
    void fastProviderIsNotAffected() {
        ProviderConfig config = new ProviderConfig();
        PaymentProviderServiceImpl service =
                new PaymentProviderServiceImpl(new MockPaymentProviderClient(config), config);

        Payment payment = new Payment();
        payment.setPaymentReference("PAY-fast");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("INR");

        assertTrue(service.createOrder(payment).startsWith("order_mock_"));
        service.shutdown();
    }
}
