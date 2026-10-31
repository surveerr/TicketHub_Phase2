package com.tickethub.eventshow.entity;

import com.tickethub.eventshow.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "shows", indexes = {@Index(columnList = "event_id,start_time"), @Index(columnList = "screen_id,start_time"), @Index(columnList = "venue_id,start_time")})
public class Show extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private Screen screen;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    @Enumerated(EnumType.STRING)
    private ShowFormat showFormat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowStatus status = ShowStatus.SCHEDULED;
    @Version
    private Long version;
}
