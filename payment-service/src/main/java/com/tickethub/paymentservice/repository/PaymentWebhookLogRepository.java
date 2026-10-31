package com.tickethub.paymentservice.repository;

import com.tickethub.paymentservice.entity.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {
    boolean existsByWebhookEventId(String webhookEventId);
    Optional<PaymentWebhookLog> findByWebhookEventId(String webhookEventId);
}
