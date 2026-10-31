package com.tickethub.paymentservice.repository;

import com.tickethub.paymentservice.entity.PaymentAttempt;
import com.tickethub.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(Long paymentId);

    long countByPaymentId(Long paymentId);

    long countByPaymentIdAndStatusAndProviderResponseStartingWith(Long paymentId, PaymentStatus status, String prefix);

    boolean existsByPaymentIdAndProviderResponseStartingWith(Long paymentId, String prefix);
}
