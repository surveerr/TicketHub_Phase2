package com.tickethub.eventshow.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "locations", uniqueConstraints = @UniqueConstraint(columnNames = {"city", "state", "country"}))
public class Location extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String city;
    @Column(nullable = false, length = 100)
    private String state;
    @Column(nullable = false, length = 100)
    private String country;
}
