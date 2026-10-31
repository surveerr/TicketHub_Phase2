package com.tickethub.booking.service;
import com.tickethub.booking.dto.request.*; import com.tickethub.booking.dto.response.*;
public interface BookingService {
    CreateBookingResponse create(CreateBookingRequest request, String idempotencyKey, Long customerId, String customerEmail);
    BookingResponse get(Long id, Long customerId);
    BookingResponse getInternal(Long id);
    void cancel(Long id, Long customerId);
    void paymentResult(Long id, PaymentResultRequest request);
    void expireBookings();
    java.util.List<Long> reservedSeats(Long showId);
    void processPaymentSucceeded(com.tickethub.booking.event.PaymentSucceededEvent event);
    void processPaymentFailed(com.tickethub.booking.event.PaymentFailedEvent event);
    void processPaymentRefunded(com.tickethub.booking.event.PaymentRefundedEvent event);
    java.util.Map<String, Object> getSagaState(Long id, Long customerId);
}
