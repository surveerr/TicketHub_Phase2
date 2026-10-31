package com.tickethub.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="bookings", indexes={@Index(name="idx_booking_user", columnList="customer_id"), @Index(name="idx_booking_show", columnList="show_id"), @Index(name="idx_booking_expiry", columnList="status,expiry_at")}, uniqueConstraints=@UniqueConstraint(name="uk_booking_idempotency", columnNames="idempotency_key"))
public class Booking {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="customer_id", nullable=false) private Long customerId;
    @Column(name="customer_email", length=150) private String customerEmail;
    @Column(name="show_id", nullable=false) private Long showId;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Column(nullable=false, length=3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private BookingStatus status;
    @Column(name="idempotency_key", length=64, unique=true) private String idempotencyKey;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="expiry_at", nullable=false) private Instant expiryAt;
    @Version private Long version;

    @PrePersist void prePersist(){ if(createdAt==null) createdAt=Instant.now(); }
    public Long getId(){return id;} public Long getCustomerId(){return customerId;} public void setCustomerId(Long v){customerId=v;} public String getCustomerEmail(){return customerEmail;} public void setCustomerEmail(String v){customerEmail=v;}
    public Long getShowId(){return showId;} public void setShowId(Long v){showId=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public BookingStatus getStatus(){return status;} public void setStatus(BookingStatus v){status=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;} public Instant getCreatedAt(){return createdAt;} public Instant getExpiryAt(){return expiryAt;} public void setExpiryAt(Instant v){expiryAt=v;}
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
}
