package com.tickethub.eventshow.entity;

import com.tickethub.eventshow.enums.ResourceStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "screens", uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "name"}))
public class Screen extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private Integer totalSeats;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status = ResourceStatus.ACTIVE;
}
