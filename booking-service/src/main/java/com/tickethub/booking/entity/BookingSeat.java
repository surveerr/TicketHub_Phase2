package com.tickethub.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="booking_seats", uniqueConstraints=@UniqueConstraint(name="uk_booking_seat", columnNames={"booking_id","seat_id"}))
public class BookingSeat {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="booking_id") private Booking booking;
    @Column(name="seat_id", nullable=false) private Long seatId;
    @Column(nullable=false, precision=10, scale=2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ReservationStatus status;
    public Long getId(){return id;} public Booking getBooking(){return booking;} public void setBooking(Booking v){booking=v;}
    public Long getSeatId(){return seatId;} public void setSeatId(Long v){seatId=v;} public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;}
    public ReservationStatus getStatus(){return status;} public void setStatus(ReservationStatus v){status=v;}
}
