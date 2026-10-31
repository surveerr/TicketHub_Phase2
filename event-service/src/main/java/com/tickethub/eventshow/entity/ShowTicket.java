package com.tickethub.eventshow.entity;

import com.tickethub.eventshow.enums.TicketCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "show_tickets", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "ticket_category"}))
public class ShowTicket extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id")
    private Show show;
    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_category", nullable = false)
    private TicketCategory ticketCategory;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
