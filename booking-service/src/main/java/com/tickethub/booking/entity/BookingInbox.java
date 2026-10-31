package com.tickethub.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "booking_inbox", uniqueConstraints = @UniqueConstraint(name = "uk_booking_inbox_event", columnNames = "event_id"))
public class BookingInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) processedAt = Instant.now();
    }

    public BookingInbox() {}

    public BookingInbox(String eventId, String eventType, Long bookingId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookingId = bookingId;
        this.processedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
