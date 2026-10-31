package com.tickethub.paymentservice.config;

import com.tickethub.paymentservice.provider.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment.provider")
public class ProviderConfig {

    private PaymentProvider name = PaymentProvider.MOCK;
    private String keyId = "";
    private String keySecret = "";
    private String webhookSecret = "change-me";
    /** Maximum time to wait for the provider before giving up with a controlled error (ST-16, SC-05). */
    private long timeoutMs = 5000;
    /** MOCK only: artificial delay before createOrder answers, to demonstrate the timeout and circuit breaker. */
    private long mockLatencyMs = 0;

    public PaymentProvider getName() { return name; }
    public void setName(PaymentProvider name) { this.name = name; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getKeySecret() { return keySecret; }
    public void setKeySecret(String keySecret) { this.keySecret = keySecret; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public long getMockLatencyMs() { return mockLatencyMs; }
    public void setMockLatencyMs(long mockLatencyMs) { this.mockLatencyMs = mockLatencyMs; }
}
