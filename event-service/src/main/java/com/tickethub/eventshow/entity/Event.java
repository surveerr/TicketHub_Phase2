package com.tickethub.eventshow.entity;

import com.tickethub.eventshow.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "events", indexes = {@Index(columnList = "title"), @Index(columnList = "status")})
public class Event extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;
    @Column(nullable = false)
    private Integer durationMinutes;
    @Enumerated(EnumType.STRING)
    private AgeRating ageRating;
    @Column(length = 500)
    private String posterUrl;
    private LocalDate releaseDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;
    @ElementCollection
    @CollectionTable(name = "event_languages", joinColumns = @JoinColumn(name = "event_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Set<Language> languages = new HashSet<>();
}
