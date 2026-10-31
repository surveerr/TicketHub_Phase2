package com.tickethub.notification.controller;

import com.tickethub.notification.dto.NotificationResponse;
import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.EventJson;
import com.tickethub.notification.repository.NotificationRepository;
import com.tickethub.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repo;
    private final NotificationService service;
    private final KafkaTemplate<String, String> kafka;
    private final String confirmedTopic;

    public NotificationController(NotificationRepository repo,
                                  NotificationService service,
                                  KafkaTemplate<String, String> kafka,
                                  @Value("${kafka.topic.booking-confirmed}") String confirmedTopic) {
        this.repo = repo;
        this.service = service;
        this.kafka = kafka;
        this.confirmedTopic = confirmedTopic;
    }

    /** Latest 50 notifications, optional ?status=SENT|FAILED|PENDING */
    @GetMapping
    public List<NotificationResponse> list(@RequestParam(required = false) Notification.Status status) {
        List<Notification> list = status == null
                ? repo.findTop50ByOrderByCreatedAtDesc()
                : repo.findTop50ByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable Long id) {
        return repo.findById(id).map(NotificationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @GetMapping("/booking/{bookingId}")
    public List<NotificationResponse> byBooking(@PathVariable Long bookingId) {
        return repo.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(NotificationResponse::from).toList();
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Notification.Status s : Notification.Status.values()) {
            m.put(s.name(), repo.countByStatus(s));
        }
        m.put("TOTAL", repo.count());
        return m;
    }

    @PostMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable Long id) {
        return NotificationResponse.from(service.retry(id));
    }

    /**
     * DEMO: publishes a sample event to Kafka, exactly like booking/payment
     * service would. Then watch the console / your inbox.
     */
    @PostMapping("/demo")
    public Map<String, Object> demo(@RequestParam String email,
                                    @RequestParam(defaultValue = "Demo Customer") String name) {
        long bookingId = System.currentTimeMillis() % 1_000_000;
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                UUID.randomUUID().toString(), bookingId, 1L, email, 10L, List.of(101L, 102L),
                new BigDecimal("500.00"), "INR", Instant.now(),
                name, "Coldplay Live", "DY Patil Stadium, Mumbai", "2026-12-20 19:00",
                List.of("A1", "A2"), "PAY-" + bookingId, null);
        kafka.send(confirmedTopic, String.valueOf(bookingId), EventJson.write(event));
        return Map.of("published", true, "topic", confirmedTopic, "bookingId", bookingId, "to", email);
    }

    /**
     * Scenario 1: Re-process a message from Dead-Letter Topic after fixing bad data.
     */
    @PostMapping("/dlt/reprocess")
    public Map<String, Object> reprocessDlt(@RequestParam String payload,
                                            @RequestParam(required = false) String targetTopic) {
        String topic = (targetTopic != null && !targetTopic.isBlank()) ? targetTopic : confirmedTopic;
        BookingConfirmedEvent event = EventJson.parse(payload);
        String key = String.valueOf(event.bookingId());
        kafka.send(topic, key, payload);
        return Map.of("status", "REPROCESSED", "topic", topic, "key", key, "bookingId", event.bookingId());
    }

    /**
     * Scenario 12: Replay mode that rebuilds notification state from history without sending real emails.
     */
    @PostMapping("/replay")
    public Map<String, Object> replay(@RequestBody List<String> eventPayloads) {
        int recorded = 0;
        for (String payload : eventPayloads) {
            try {
                service.recordReplay(payload);
                recorded++;
            } catch (Exception ignored) {}
        }
        return Map.of("mode", "REPLAY_AUDIT", "recordsRebuilt", recorded, "emailsDispatched", 0);
    }
}
