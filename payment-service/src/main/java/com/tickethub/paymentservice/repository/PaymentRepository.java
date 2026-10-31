package com.tickethub.paymentservice.repository;

import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    Optional<Payment> findTopByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);

    /** The single live (PENDING/SUCCESS) payment of a booking, if any. */
    Optional<Payment> findByActiveBookingId(Long bookingId);

    boolean existsByActiveBookingId(Long bookingId);

    /** Final results that have not been delivered to Booking yet (older than the cutoff, so inline delivery gets first go). */
    List<Payment> findByBookingSyncPendingTrueAndUpdatedAtBefore(LocalDateTime before);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);

    /** Row lock, used so a webhook and a verify call cannot update the same payment at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentReference = :reference")
    Optional<Payment> findByPaymentReferenceForUpdate(@Param("reference") String reference);
}
