package com.tickethub.booking.service.impl;

import com.tickethub.booking.client.EventClient;
import com.tickethub.booking.dto.request.*;
import com.tickethub.booking.dto.response.*;
import com.tickethub.booking.entity.*;
import com.tickethub.booking.exception.*;
import com.tickethub.booking.repository.*;
import com.tickethub.booking.service.BookingService;
import com.tickethub.booking.service.BookingEventPublisher;
import com.tickethub.booking.event.BookingConfirmedEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@org.springframework.transaction.annotation.Transactional
public class BookingServiceImpl implements BookingService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BookingServiceImpl.class);
    private static final DateTimeFormatter SHOW_TIME=DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z",Locale.ENGLISH);
    private final ZoneId notificationZone;
    private final BookingRepository bookingRepository; private final BookingSeatRepository seatRepository; private final EventClient eventClient;
    private final long expirySeconds; private final BookingEventPublisher eventPublisher;
    private final com.tickethub.booking.repository.BookingInboxRepository inboxRepository;

    public BookingServiceImpl(BookingRepository b, BookingSeatRepository s, EventClient e,
                              BookingEventPublisher eventPublisherArg,
                              com.tickethub.booking.repository.BookingInboxRepository inboxRepository,
                              org.springframework.core.env.Environment env){
        bookingRepository=b; seatRepository=s; eventClient=e; eventPublisher=eventPublisherArg;
        this.inboxRepository=inboxRepository;
        expirySeconds=Long.parseLong(env.getProperty("booking.hold-expiry-seconds","600"));
        notificationZone=ZoneId.of(env.getProperty("booking.notification-zone","Asia/Kolkata"));
    }

    @Override public CreateBookingResponse create(CreateBookingRequest request,String idempotencyKey,Long customerId,String customerEmail){
        if(customerId==null || customerId<1) throw new BookingBusinessException("A valid customer id is required");
        String key=normalizeKey(idempotencyKey);
        if(key!=null){ Optional<Booking> existing=bookingRepository.findByIdempotencyKey(key); if(existing.isPresent()){ Booking prior=existing.get(); if(!Objects.equals(customerId,prior.getCustomerId())) throw new BookingBusinessException("Idempotency-Key is already associated with another customer"); return toCreate(prior,"Idempotent replay: existing booking returned"); } }
        validateSeats(request.seatIds());
        EventClient.ShowInfo show=eventClient.getShow(request.showId());
        if(!"ACTIVE".equals(show.eventStatus())) throw new BookingBusinessException("EVENT_NOT_PUBLISHED: event is not published");
        if(!"BOOKING_OPEN".equals(show.status())) throw new BookingBusinessException("SHOW_NOT_OPEN: show is not open for booking");
        if(show.startTime()!=null && !show.startTime().isAfter(Instant.now())) throw new BookingBusinessException("SHOW_ALREADY_STARTED: booking is not allowed");
        Map<Long,EventClient.SeatInfo> seats=eventClient.getShowSeats(request.showId()).stream().collect(java.util.stream.Collectors.toMap(EventClient.SeatInfo::seatId,x->x,(a,b)->a));
        BigDecimal amount=BigDecimal.ZERO;
        for(Long id:request.seatIds()){
            EventClient.SeatInfo s=seats.get(id);
            if(s==null) throw new BookingBusinessException("SEAT_NOT_FOUND: "+id);
            if(s.price()==null || s.price().signum()<0) throw new BookingBusinessException("Invalid seat price for seat "+id);
            amount=amount.add(s.price());
        }
        Instant expiry=Instant.now().plusSeconds(expirySeconds);
        Booking booking=new Booking(); booking.setCustomerId(customerId); booking.setCustomerEmail(customerEmail); booking.setShowId(request.showId()); booking.setAmount(amount); booking.setCurrency("INR"); booking.setStatus(BookingStatus.PENDING_PAYMENT); booking.setIdempotencyKey(key); booking.setExpiryAt(expiry);
        try { booking=bookingRepository.saveAndFlush(booking); org.slf4j.MDC.put("bookingId", String.valueOf(booking.getId())); }
        catch(DataIntegrityViolationException ex){ if(key!=null) return toCreate(bookingRepository.findByIdempotencyKey(key).orElseThrow(()->ex),"Idempotent replay: existing booking returned"); throw ex; }
        try {
            eventClient.holdSeats(request.showId(),booking.getId(),request.seatIds(),expiry);
            for(Long id:request.seatIds()){ BookingSeat bs=new BookingSeat(); bs.setBooking(booking); bs.setSeatId(id); bs.setPrice(seats.get(id).price()); bs.setStatus(ReservationStatus.HELD); seatRepository.save(bs); }
            return new CreateBookingResponse(booking.getId(),booking.getShowId(),booking.getAmount(),booking.getCurrency(),booking.getStatus().name(),booking.getExpiryAt(),"Seats held. Proceed to payment.");
        } catch(RuntimeException ex){
            try{ eventClient.releaseSeats(request.showId(),booking.getId()); }catch(Exception ignored){}
            booking.setStatus(BookingStatus.CANCELLED); bookingRepository.save(booking);
            throw new BookingBusinessException(ex.getMessage()==null?"Unable to reserve seats":ex.getMessage(),ex);
        }
    }

    @Override public BookingResponse get(Long id,Long customerId){ Booking b=bookingRepository.findById(id).orElseThrow(()->new BookingNotFoundException("Booking not found: "+id)); if(!Objects.equals(customerId,b.getCustomerId())) throw new BookingBusinessException("You are not allowed to view this booking"); return BookingResponse.from(b,seatRepository.findByBookingId(id)); }
    @Override public BookingResponse getInternal(Long id){ Booking b=bookingRepository.findById(id).orElseThrow(()->new BookingNotFoundException("Booking not found: "+id)); return BookingResponse.from(b,seatRepository.findByBookingId(id)); }
    @Override public java.util.List<Long> reservedSeats(Long showId){ return seatRepository.findAll().stream().filter(x->x.getBooking()!=null && Objects.equals(x.getBooking().getShowId(),showId) && (x.getStatus()==ReservationStatus.HELD || x.getStatus()==ReservationStatus.BOOKED)).map(BookingSeat::getSeatId).toList(); }

    @Override public void cancel(Long id, Long customerId){
        Booking b=bookingRepository.findById(id).orElseThrow(()->new BookingNotFoundException("Booking not found: "+id));
        if(customerId!=null && !Objects.equals(customerId,b.getCustomerId())) throw new BookingBusinessException("You are not allowed to cancel this booking");
        if(b.getStatus()==BookingStatus.CANCELLED || b.getStatus()==BookingStatus.EXPIRED || b.getStatus()==BookingStatus.PAYMENT_FAILED || b.getStatus()==BookingStatus.REFUNDED) return;
        if(b.getStatus()==BookingStatus.CONFIRMED) {
            log.info("Cancelling CONFIRMED booking {}. Initiating Saga compensation refund.", id);
            b.setStatus(BookingStatus.COMPENSATING);
            bookingRepository.save(b);
            eventClient.releaseSeats(b.getShowId(), b.getId());
            markSeatsReleased(b.getId());
            eventPublisher.publishBookingCancelled(new com.tickethub.booking.event.BookingCancelledEvent(
                    UUID.randomUUID().toString(), "BookingCancelled", b.getId(), "Customer requested cancellation",
                    b.getVersion()!=null ? b.getVersion() : 1L, Instant.now()
            ));
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
            notifyCancelled(b, null, "You cancelled this booking. Refund compensation has been requested.");
            return;
        }
        eventClient.releaseSeats(b.getShowId(),b.getId()); b.setStatus(BookingStatus.CANCELLED); bookingRepository.save(b); markSeatsReleased(b.getId());
        notifyCancelled(b,null,"You cancelled this booking.");
    }

    @Override
    public void processPaymentSucceeded(com.tickethub.booking.event.PaymentSucceededEvent event) {
        String eventId = event.eventId() != null ? event.eventId() : "PaymentSucceeded:" + event.bookingId();
        if (inboxRepository.existsByEventId(eventId)) {
            log.info("PaymentSucceeded event {} already processed in inbox. Skipping duplicate.", eventId);
            return;
        }
        inboxRepository.save(new com.tickethub.booking.entity.BookingInbox(eventId, "PaymentSucceeded", event.bookingId()));

        Optional<Booking> opt = bookingRepository.findById(event.bookingId());
        if (opt.isEmpty()) {
            log.warn("Booking {} not found for PaymentSucceeded event. Rejecting for compensation refund.", event.bookingId());
            eventPublisher.publishBookingRejected(new com.tickethub.booking.event.BookingRejectedEvent(
                    UUID.randomUUID().toString(), "BookingRejected", event.bookingId(), "Booking not found",
                    event.aggregateVersion(), Instant.now()));
            return;
        }

        Booking b = opt.get();
        if (event.aggregateVersion() != null && b.getVersion() != null && event.aggregateVersion() < b.getVersion()) {
            log.warn("Stale event ignored for booking {}: event version {} < booking version {}",
                    b.getId(), event.aggregateVersion(), b.getVersion());
            return;
        }

        if (b.getStatus() == BookingStatus.CONFIRMED) {
            log.info("Booking {} already CONFIRMED. Idempotent return.", b.getId());
            return;
        }

        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            log.warn("Booking {} in status {} cannot be confirmed. Triggering compensation refund.", b.getId(), b.getStatus());
            eventPublisher.publishBookingRejected(new com.tickethub.booking.event.BookingRejectedEvent(
                    UUID.randomUUID().toString(), "BookingRejected", b.getId(),
                    "Reservation was already " + b.getStatus(), event.aggregateVersion(), Instant.now()));
            notifyPaymentFailed(b, event.paymentReference(), "Reservation was " + b.getStatus() + ". Refund initiated.");
            return;
        }

        if (b.getExpiryAt().isBefore(Instant.now())) {
            log.warn("Hold expired for booking {}. Triggering compensation refund.", b.getId());
            b.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(b);
            try { eventClient.releaseSeats(b.getShowId(), b.getId()); } catch (Exception ignored) {}
            markSeatsReleased(b.getId());
            eventPublisher.publishBookingRejected(new com.tickethub.booking.event.BookingRejectedEvent(
                    UUID.randomUUID().toString(), "BookingRejected", b.getId(),
                    "Hold expired before payment confirmation", event.aggregateVersion(), Instant.now()));
            notifyPaymentFailed(b, event.paymentReference(), "Seat hold expired. Refund initiated.");
            return;
        }

        eventClient.confirmSeats(b.getShowId(), b.getId());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setVersion((b.getVersion() != null ? b.getVersion() : 1L) + 1);
        bookingRepository.save(b);
        markSeatsBooked(b.getId());
        notifyConfirmed(b, event.paymentReference());
        log.info("Booking {} CONFIRMED via Kafka Saga and seats booked.", b.getId());
    }

    @Override
    public void processPaymentFailed(com.tickethub.booking.event.PaymentFailedEvent event) {
        String eventId = event.eventId() != null ? event.eventId() : "PaymentFailed:" + event.bookingId();
        if (inboxRepository.existsByEventId(eventId)) {
            log.info("PaymentFailed event {} already processed in inbox. Skipping duplicate.", eventId);
            return;
        }
        inboxRepository.save(new com.tickethub.booking.entity.BookingInbox(eventId, "PaymentFailed", event.bookingId()));

        Optional<Booking> opt = bookingRepository.findById(event.bookingId());
        if (opt.isEmpty()) return;
        Booking b = opt.get();
        if (b.getStatus() == BookingStatus.PAYMENT_FAILED || b.getStatus() == BookingStatus.CANCELLED) return;

        b.setStatus(BookingStatus.PAYMENT_FAILED);
        b.setVersion((b.getVersion() != null ? b.getVersion() : 1L) + 1);
        bookingRepository.save(b);
        try { eventClient.releaseSeats(b.getShowId(), b.getId()); } finally { markSeatsReleased(b.getId()); }
        notifyPaymentFailed(b, event.paymentReference(), event.failureReason());
        log.info("Booking {} marked PAYMENT_FAILED via Kafka Saga and seats released.", b.getId());
    }

    @Override
    public void processPaymentRefunded(com.tickethub.booking.event.PaymentRefundedEvent event) {
        String eventId = event.eventId() != null ? event.eventId() : "PaymentRefunded:" + event.bookingId();
        if (inboxRepository.existsByEventId(eventId)) {
            log.info("PaymentRefunded event {} already processed in inbox. Skipping duplicate.", eventId);
            return;
        }
        inboxRepository.save(new com.tickethub.booking.entity.BookingInbox(eventId, "PaymentRefunded", event.bookingId()));

        bookingRepository.findById(event.bookingId()).ifPresent(b -> {
            b.setStatus(BookingStatus.REFUNDED);
            b.setVersion((b.getVersion() != null ? b.getVersion() : 1L) + 1);
            bookingRepository.save(b);
            log.info("Booking {} marked as REFUNDED following completed Saga compensation.", b.getId());
        });
    }

    @Override
    public java.util.Map<String, Object> getSagaState(Long id, Long customerId) {
        Booking b = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException("Booking not found: " + id));
        if (customerId != null && !Objects.equals(customerId, b.getCustomerId())) {
            throw new BookingBusinessException("You are not allowed to view this booking's saga state");
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("bookingId", b.getId());
        state.put("status", b.getStatus().name());
        state.put("version", b.getVersion());
        state.put("amount", b.getAmount());
        state.put("currency", b.getCurrency());
        state.put("expiryAt", b.getExpiryAt());
        state.put("isExpired", b.getExpiryAt().isBefore(Instant.now()));
        state.put("sagaPhase", switch (b.getStatus()) {
            case PENDING_PAYMENT -> "STEP_1_HOLD_ACTIVE";
            case CONFIRMED -> "STEP_2_CONFIRMED";
            case COMPENSATING -> "STEP_3_COMPENSATING_REFUND";
            case CANCELLED -> "STEP_3_CANCELLED_COMPENSATING";
            case REFUNDED -> "STEP_4_COMPENSATED_FINAL";
            case PAYMENT_FAILED -> "STEP_2_FAILED_RELEASED";
            case EXPIRED -> "STEP_2_EXPIRED_RELEASED";
        });
        return state;
    }

    @Override public void paymentResult(Long id,PaymentResultRequest request){
        Booking b=bookingRepository.findById(id).orElseThrow(()->new BookingNotFoundException("Booking not found: "+id));
        if(request.status()==PaymentResultRequest.PaymentResultStatus.SUCCESS){
            if(b.getStatus()==BookingStatus.CONFIRMED) return;
            if(b.getStatus()!=BookingStatus.PENDING_PAYMENT){ notifyPaymentFailed(b,request.paymentReference(),"Your reservation was already "+b.getStatus()+" when the payment completed. If any amount was debited it will be reviewed for refund."); throw new BookingBusinessException("Booking cannot be confirmed from status "+b.getStatus()); }
            if(b.getExpiryAt().isBefore(Instant.now())) { b.setStatus(BookingStatus.EXPIRED); bookingRepository.save(b); try{eventClient.releaseSeats(b.getShowId(),b.getId());}catch(Exception ignored){}; notifyPaymentFailed(b,request.paymentReference(),"Your seat hold expired before the payment was confirmed. If any amount was debited it will be reviewed for refund."); throw new BookingBusinessException("HOLD_EXPIRED: booking has expired"); }
            eventClient.confirmSeats(b.getShowId(),b.getId()); b.setStatus(BookingStatus.CONFIRMED); bookingRepository.save(b); markSeatsBooked(b.getId()); notifyConfirmed(b,request.paymentReference());
        } else {
            if(b.getStatus()==BookingStatus.PAYMENT_FAILED) return;
            if(b.getStatus()==BookingStatus.CONFIRMED) throw new BookingBusinessException("A confirmed booking cannot be marked payment failed");
            b.setStatus(BookingStatus.PAYMENT_FAILED); bookingRepository.save(b);
            notifyPaymentFailed(b,request.paymentReference(),request.failureReason()==null||request.failureReason().isBlank()?"The payment was not successful.":request.failureReason());
            try{eventClient.releaseSeats(b.getShowId(),b.getId());}finally{markSeatsReleased(b.getId());}
        }
    }

    @Override public void expireBookings(){
        for(Booking b:bookingRepository.findByStatusAndExpiryAtBefore(BookingStatus.PENDING_PAYMENT,Instant.now())){
            try{ eventClient.releaseSeats(b.getShowId(),b.getId()); } finally { b.setStatus(BookingStatus.EXPIRED); bookingRepository.save(b); markSeatsReleased(b.getId()); }
            notifyCancelled(b,null,"The payment was not completed in time, so your seats were released.");
        }
    }
    // ---- notifications (Kafka -> Notification Service -> customer e-mail). Never throws. ----
    private void notifyConfirmed(Booking b,String paymentRef){ try{ eventPublisher.publishConfirmed(buildEvent(b,paymentRef,null)); }catch(Exception e){ log.error("Confirmed notification failed for booking {}",b.getId(),e); } }
    private void notifyPaymentFailed(Booking b,String paymentRef,String reason){ try{ eventPublisher.publishPaymentFailed(buildEvent(b,paymentRef,reason)); }catch(Exception e){ log.error("Payment-failed notification failed for booking {}",b.getId(),e); } }
    private void notifyCancelled(Booking b,String paymentRef,String reason){ try{ eventPublisher.publishCancelled(buildEvent(b,paymentRef,reason)); }catch(Exception e){ log.error("Cancelled notification failed for booking {}",b.getId(),e); } }
    private BookingConfirmedEvent buildEvent(Booking b,String paymentRef,String reason){
        List<Long> seatIds=seatRepository.findByBookingId(b.getId()).stream().map(BookingSeat::getSeatId).toList();
        return new BookingConfirmedEvent(UUID.randomUUID().toString(),b.getId(),b.getCustomerId(),b.getCustomerEmail(),b.getShowId(),seatIds,b.getAmount(),b.getCurrency(),Instant.now(),showTimeText(b.getShowId()),paymentRef,reason);
    }
    private String showTimeText(Long showId){ try{ EventClient.ShowInfo show=eventClient.getShow(showId); return show==null||show.startTime()==null?null:SHOW_TIME.format(show.startTime().atZone(notificationZone)); }catch(Exception e){ return null; } }
    private void markSeatsReleased(Long bookingId){ var seats=seatRepository.findByBookingId(bookingId); seats.forEach(x->x.setStatus(ReservationStatus.RELEASED)); seatRepository.saveAll(seats); }
    private void markSeatsBooked(Long bookingId){ var seats=seatRepository.findByBookingId(bookingId); seats.forEach(x->x.setStatus(ReservationStatus.BOOKED)); seatRepository.saveAll(seats); }
    private void validateSeats(List<Long> ids){ if(ids==null||ids.isEmpty()||ids.size()>10) throw new BookingBusinessException("Select between 1 and 10 seats"); if(new HashSet<>(ids).size()!=ids.size()) throw new BookingBusinessException("DUPLICATE_SEATS: duplicate seat ids are not allowed"); }
    private String normalizeKey(String key){ if(key==null||key.isBlank())return null; if(key.length()>64)throw new BookingBusinessException("Idempotency-Key must be at most 64 characters"); return key.trim(); }
    private CreateBookingResponse toCreate(Booking b,String message){ if(b!=null && b.getId()!=null) org.slf4j.MDC.put("bookingId", String.valueOf(b.getId())); return new CreateBookingResponse(b.getId(),b.getShowId(),b.getAmount(),b.getCurrency(),b.getStatus().name(),b.getExpiryAt(),message);}
}
