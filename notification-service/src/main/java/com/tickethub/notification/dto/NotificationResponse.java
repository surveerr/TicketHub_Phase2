package com.tickethub.notification.dto;

import com.tickethub.notification.entity.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        Long bookingId,
        Long customerId,
        String recipientEmail,
        String subject,
        String status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().name(), n.getBookingId(), n.getCustomerId(),
                n.getRecipientEmail(), n.getSubject(), n.getStatus().name(), n.getAttempts(),
                n.getLastError(), n.getCreatedAt(), n.getSentAt());
    }
}
