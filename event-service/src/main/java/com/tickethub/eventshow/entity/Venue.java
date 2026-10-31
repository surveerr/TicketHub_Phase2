package com.tickethub.eventshow.entity;

import com.tickethub.eventshow.enums.ResourceStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "venues", indexes = @Index(columnList = "location_id"))
public class Venue extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "address_line", nullable = false)
    private String addressLine;
    private String pincode;
    private Double latitude;
    private Double longitude;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status = ResourceStatus.ACTIVE;
}
