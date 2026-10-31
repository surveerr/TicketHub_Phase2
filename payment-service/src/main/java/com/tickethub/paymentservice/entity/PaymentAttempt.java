package com.tickethub.paymentservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_response", length = 500)
    private String providerResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(Payment payment, int attemptNumber, PaymentStatus status, String providerResponse) {
        this.payment = payment;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.providerResponse = providerResponse != null && providerResponse.length() > 500
                ? providerResponse.substring(0, 500) : providerResponse;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public int getAttemptNumber() { return attemptNumber; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderResponse() { return providerResponse; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
