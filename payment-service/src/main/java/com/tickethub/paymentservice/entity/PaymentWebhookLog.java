package com.tickethub.paymentservice.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_webhook_log", uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_id", columnNames = "webhook_event_id"))
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_event_id", nullable = false, length = 120)
    private String webhookEventId;

    @Column(name = "provider_order_id", length = 100)
    private String providerOrderId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) processedAt = Instant.now();
    }

    public PaymentWebhookLog() {}

    public PaymentWebhookLog(String webhookEventId, String providerOrderId, String eventType) {
        this.webhookEventId = webhookEventId;
        this.providerOrderId = providerOrderId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getWebhookEventId() { return webhookEventId; }
    public void setWebhookEventId(String webhookEventId) { this.webhookEventId = webhookEventId; }
    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
