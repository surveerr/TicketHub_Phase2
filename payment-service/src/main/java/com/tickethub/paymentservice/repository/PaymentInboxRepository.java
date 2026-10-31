package com.tickethub.paymentservice.repository;

import com.tickethub.paymentservice.entity.PaymentInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentInboxRepository extends JpaRepository<PaymentInbox, Long> {
    boolean existsByEventId(String eventId);
    Optional<PaymentInbox> findByEventId(String eventId);
}
