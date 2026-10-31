package com.tickethub.notification.service;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.EventJson;
import com.tickethub.notification.event.NotificationType;
import com.tickethub.notification.exception.InvalidEventException;
import com.tickethub.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final EmailDeliveryService emailDelivery;
    private final EmailTemplateService templates;
    private final int maxAttempts;

    public NotificationService(NotificationRepository repo,
                               EmailDeliveryService emailDelivery,
                               EmailTemplateService templates,
                               @Value("${notification.retry.max-attempts:5}") int maxAttempts) {
        this.repo = repo;
        this.emailDelivery = emailDelivery;
        this.templates = templates;
        this.maxAttempts = maxAttempts;
    }

    /** Called by the Kafka listeners. */
    public void handle(NotificationType type, String payload) {
        BookingConfirmedEvent event = EventJson.parse(payload);

        if (event.bookingId() == null) {
            throw new InvalidEventException("bookingId is missing in Kafka event");
        }
        if (event.recipientEmail() == null || !event.recipientEmail().contains("@")) {
            throw new InvalidEventException("recipientEmail missing/invalid for booking " + event.bookingId());
        }

        String key = (event.eventId() == null || event.eventId().isBlank())
                ? type + ":" + event.bookingId()
                : type + ":" + event.eventId();

        Notification existing = repo.findByEventId(key).orElse(null);
        if (existing != null && existing.getStatus() == Notification.Status.SENT) {
            log.info("Duplicate {} for booking {} ignored (already sent)", type, event.bookingId());
            return;
        }

        Notification n = existing != null ? existing : new Notification();
        n.setEventId(key);
        n.setType(type);
        n.setBookingId(event.bookingId());
        n.setCustomerId(event.customerId());
        n.setRecipientEmail(event.recipientEmail());
        n.setPayload(payload);
        if (n.getStatus() == null) {
            n.setStatus(Notification.Status.PENDING);
        }
        n = repo.save(n);

        deliver(n, event);
    }

    /** Manual retry (REST). Works even if max attempts was reached. */
    public Notification retry(Long id) {
        Notification n = repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification " + id + " not found"));
        if (n.getStatus() == Notification.Status.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Notification already sent");
        }
        deliver(n, EventJson.parse(n.getPayload()));
        return n;
    }

    /** Automatic retry of failed e-mails (e.g. SMTP was down). */
    @Scheduled(fixedDelayString = "${notification.retry.interval-ms:60000}",
            initialDelayString = "${notification.retry.interval-ms:60000}")
    public void retryFailed() {
        List<Notification> failed = repo.findByStatusAndAttemptsLessThan(Notification.Status.FAILED, maxAttempts);
        if (failed.isEmpty()) {
            return;
        }
        log.info("Retrying {} failed notification(s)", failed.size());
        for (Notification n : failed) {
            try {
                deliver(n, EventJson.parse(n.getPayload()));
            } catch (Exception ex) {
                log.error("Retry of notification {} crashed: {}", n.getId(), ex.getMessage());
            }
        }
    }

    /** Scenario 12: Replay mode that rebuilds notification records without sending real emails. */
    public void recordReplay(String payload) {
        BookingConfirmedEvent event = EventJson.parse(payload);
        if (event.bookingId() == null) return;
        String key = "REPLAY:" + (event.eventId() != null ? event.eventId() : event.bookingId());
        Notification n = repo.findByEventId(key).orElse(new Notification());
        n.setEventId(key);
        n.setType(NotificationType.BOOKING_CONFIRMED);
        n.setBookingId(event.bookingId());
        n.setCustomerId(event.customerId());
        n.setRecipientEmail(event.recipientEmail());
        n.setPayload(payload);
        n.setStatus(Notification.Status.SENT);
        n.setSentAt(Instant.now());
        n.setSubject("[REPLAY REBUILT] Booking #" + event.bookingId());
        repo.save(n);
        log.info("Historical state rebuilt for booking {} (emails skipped)", event.bookingId());
    }

    private void deliver(Notification n, BookingConfirmedEvent event) {
        n.setAttempts(n.getAttempts() + 1);
        try {
            EmailContent content = templates.build(n.getType(), event);
            n.setSubject(content.subject());
            emailDelivery.send(n.getRecipientEmail(), content);
            n.setStatus(Notification.Status.SENT);
            n.setSentAt(Instant.now());
            n.setLastError(null);
            log.info("Notification {} ({}) SENT to {} for booking {}",
                    n.getId(), n.getType(), n.getRecipientEmail(), n.getBookingId());
        } catch (Exception ex) {
            n.setStatus(Notification.Status.FAILED);
            n.setLastError(truncate(ex.getMessage()));
            log.error("Notification {} FAILED (attempt {}): {}", n.getId(), n.getAttempts(), ex.getMessage());
        }
        repo.save(n);
    }

    private static String truncate(String s) {
        if (s == null) return "unknown error";
        return s.length() > 490 ? s.substring(0, 490) : s;
    }
}
