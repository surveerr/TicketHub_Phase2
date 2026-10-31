package com.tickethub.notification.repository;

import com.tickethub.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventId(String eventId);

    List<Notification> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    List<Notification> findTop50ByOrderByCreatedAtDesc();

    List<Notification> findTop50ByStatusOrderByCreatedAtDesc(Notification.Status status);

    List<Notification> findByStatusAndAttemptsLessThan(Notification.Status status, int attempts);

    long countByStatus(Notification.Status status);
}
