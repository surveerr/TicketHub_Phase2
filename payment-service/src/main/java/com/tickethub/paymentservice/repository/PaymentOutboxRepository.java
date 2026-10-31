package com.tickethub.paymentservice.repository;

import com.tickethub.paymentservice.entity.PaymentOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, Long> {
    List<PaymentOutbox> findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(
            PaymentOutbox.Status status, Instant before);
}
