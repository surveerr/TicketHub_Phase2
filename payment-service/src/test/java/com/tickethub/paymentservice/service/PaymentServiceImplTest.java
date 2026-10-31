package com.tickethub.paymentservice.service;

import com.tickethub.paymentservice.client.BookingClient;
import com.tickethub.paymentservice.client.BookingDetails;
import com.tickethub.paymentservice.client.BookingPaymentResult;
import com.tickethub.paymentservice.client.BookingSyncResult;
import com.tickethub.paymentservice.config.ProviderConfig;
import com.tickethub.paymentservice.dto.request.CreatePaymentRequest;
import com.tickethub.paymentservice.dto.response.PaymentResponse;
import com.tickethub.paymentservice.entity.Payment;
import com.tickethub.paymentservice.entity.PaymentStatus;
import com.tickethub.paymentservice.exception.BookingNotPayableException;
import com.tickethub.paymentservice.exception.ProviderUnavailableException;
import com.tickethub.paymentservice.provider.PaymentProvider;
import com.tickethub.paymentservice.repository.PaymentAttemptRepository;
import com.tickethub.paymentservice.repository.PaymentRepository;
import com.tickethub.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentAttemptRepository attemptRepository;
    @Mock PaymentProviderService providerService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock BookingClient bookingClient;

    private ProviderConfig providerConfig;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        providerConfig = new ProviderConfig();
        // A real TransactionTemplate over a mocked transaction manager just runs the callback.
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new PaymentServiceImpl(paymentRepository, attemptRepository, providerService, providerConfig,
                eventPublisher, tx, bookingClient, 1, "INR");

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(providerService.getPublicKey()).thenReturn("rzp_test_key");
    }

    private static BookingDetails booking(String status, java.time.Instant expiresAt) {
        return new BookingDetails(5L, 7L, new BigDecimal("750.00"), "INR", status, expiresAt);
    }

    private static Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setPaymentReference("PAY-1");
        p.setBookingId(5L);
        p.setUserId(7L);
        p.setAmount(new BigDecimal("750.00"));
        p.setCurrency("INR");
        p.setStatus(status);
        p.setProvider(PaymentProvider.MOCK);
        p.setProviderOrderId("order_1");
        p.setActiveBookingId(5L);
        return p;
    }

    // ---- ST-46 / ST-54: amount and owner come from the booking ----

    @Test
    void createPayment_takesAmountCurrencyAndOwnerFromBooking() {
        when(bookingClient.getBooking(5L)).thenReturn(booking("PENDING_PAYMENT", java.time.Instant.now().plusSeconds(600)));
        when(paymentRepository.findByActiveBookingId(5L)).thenReturn(Optional.empty());
        when(providerService.createOrder(any(Payment.class))).thenReturn("order_1");

        PaymentResponse response = service.createPayment(new CreatePaymentRequest(5L, null), 7L);

        assertEquals(new BigDecimal("750.00"), response.amount());
        assertEquals("INR", response.currency());
        assertEquals(7L, response.userId());
        assertEquals(5L, response.bookingId());
        assertEquals("order_1", response.providerOrderId());
        assertEquals(PaymentStatus.PENDING, response.status());
    }

    @Test
    void createPayment_rejectsBookingThatIsNotPendingPayment() {
        when(bookingClient.getBooking(5L)).thenReturn(booking("CONFIRMED", null));

        assertThrows(BookingNotPayableException.class,
                () -> service.createPayment(new CreatePaymentRequest(5L, null), 7L));
        verify(providerService, never()).createOrder(any(Payment.class));
    }

    @Test
    void createPayment_rejectsExpiredReservation() {
        when(bookingClient.getBooking(5L)).thenReturn(booking("PENDING_PAYMENT", java.time.Instant.now().minusSeconds(60)));

        assertThrows(BookingNotPayableException.class,
                () -> service.createPayment(new CreatePaymentRequest(5L, null), 7L));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ---- ST-24: no second payment for the same booking ----

    @Test
    void createPayment_returnsTheExistingLivePaymentInsteadOfCreatingASecondOne() {
        when(bookingClient.getBooking(5L)).thenReturn(booking("PENDING_PAYMENT", java.time.Instant.now().plusSeconds(600)));
        when(paymentRepository.findByActiveBookingId(5L)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        PaymentResponse response = service.createPayment(new CreatePaymentRequest(5L, null), 7L);

        assertEquals("PAY-1", response.paymentReference());
        verify(providerService, never()).createOrder(any(Payment.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_replaysTheSamePaymentForTheSameIdempotencyKey() {
        when(paymentRepository.findByIdempotencyKey("k1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        PaymentResponse response = service.createPayment(new CreatePaymentRequest(5L, "k1"), 7L);

        assertEquals("PAY-1", response.paymentReference());
        verify(bookingClient, never()).getBooking(any());
    }

    @Test
    void createPayment_failedOrderCreationFreesTheBookingSlotAndReturnsControlledError() {
        when(bookingClient.getBooking(5L)).thenReturn(booking("PENDING_PAYMENT", java.time.Instant.now().plusSeconds(600)));
        when(paymentRepository.findByActiveBookingId(5L)).thenReturn(Optional.empty());
        when(providerService.createOrder(any(Payment.class))).thenThrow(new RuntimeException("provider down"));

        assertThrows(ProviderUnavailableException.class,
                () -> service.createPayment(new CreatePaymentRequest(5L, null), 7L));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Payment last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertEquals(PaymentStatus.FAILED, last.getStatus());
        assertNull(last.getActiveBookingId());
    }

    // ---- ST-55 / ST-56: Booking is told the result ----

    @Test
    void successfulPayment_tellsBookingAndClearsThePendingFlag() {
        Payment p = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(p));
        when(bookingClient.notifyPaymentResult(eq(5L), any(BookingPaymentResult.class)))
                .thenReturn(BookingSyncResult.DELIVERED);

        service.applyStatusUpdate("PAY-1", PaymentStatus.SUCCESS, "pay_abc", null);

        ArgumentCaptor<BookingPaymentResult> sent = ArgumentCaptor.forClass(BookingPaymentResult.class);
        verify(bookingClient).notifyPaymentResult(eq(5L), sent.capture());
        assertEquals("SUCCESS", sent.getValue().status());
        assertEquals("PAY-1", sent.getValue().paymentReference());
        assertEquals("pay_abc", sent.getValue().providerTransactionId());
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertFalse(p.isBookingSyncPending());
    }

    @Test
    void failedPayment_tellsBookingToReleaseSeatsAndFreesTheSlot() {
        Payment p = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(p));
        when(bookingClient.notifyPaymentResult(eq(5L), any(BookingPaymentResult.class)))
                .thenReturn(BookingSyncResult.DELIVERED);

        service.applyStatusUpdate("PAY-1", PaymentStatus.FAILED, null, "card declined");

        ArgumentCaptor<BookingPaymentResult> sent = ArgumentCaptor.forClass(BookingPaymentResult.class);
        verify(bookingClient).notifyPaymentResult(eq(5L), sent.capture());
        assertEquals("FAILED", sent.getValue().status());
        assertEquals("card declined", sent.getValue().failureReason());
        assertNull(p.getActiveBookingId());
    }

    @Test
    void whenBookingIsDown_resultStaysPendingForTheRetryJob() {
        Payment p = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(p));
        when(bookingClient.notifyPaymentResult(eq(5L), any(BookingPaymentResult.class)))
                .thenReturn(BookingSyncResult.RETRY);

        service.applyStatusUpdate("PAY-1", PaymentStatus.SUCCESS, "pay_abc", null);

        assertEquals(PaymentStatus.SUCCESS, p.getStatus());     // payment state is never lost
        assertTrue(p.isBookingSyncPending());                   // ...and the result is still owed to Booking
        assertFalse(service.syncBookingResult("PAY-1"));        // the retry job would try again
    }

    @Test
    void duplicateSuccessWebhook_doesNotNotifyBookingAgain() {
        Payment p = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-1")).thenReturn(Optional.of(p));

        service.applyStatusUpdate("PAY-1", PaymentStatus.SUCCESS, "pay_abc", null);

        verify(bookingClient, never()).notifyPaymentResult(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void bookingRefusingTheResult_isRecordedAndNotRetriedForever() {
        Payment p = payment(PaymentStatus.SUCCESS);
        p.setBookingSyncPending(true);
        when(paymentRepository.findByPaymentReference("PAY-1")).thenReturn(Optional.of(p));
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-1")).thenReturn(Optional.of(p));
        when(bookingClient.notifyPaymentResult(eq(5L), any(BookingPaymentResult.class)))
                .thenReturn(BookingSyncResult.REJECTED);

        assertTrue(service.syncBookingResult("PAY-1"));

        assertFalse(p.isBookingSyncPending());
        assertNotNull(p.getFailureReason());                    // outcome recorded for review/refund (SC-12)
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
    }
}
