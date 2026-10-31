package com.tickethub.eventshow.entity;

import com.tickethub.eventshow.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "show_seats", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}), indexes = @Index(columnList = "show_id,status"))
public class ShowSeat extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id")
    private Show show;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    private Long heldByBookingId;
    private Instant holdExpiresAt;
    @Version
    private Long version;
}
