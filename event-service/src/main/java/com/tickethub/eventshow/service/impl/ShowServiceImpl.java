package com.tickethub.eventshow.service.impl;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.entity.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.exception.*;
import com.tickethub.eventshow.mapper.EventShowMapper;
import com.tickethub.eventshow.repository.*;
import com.tickethub.eventshow.service.ShowService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class ShowServiceImpl implements ShowService {
    private final ShowRepository showRepo;
    private final EventRepository eventRepo;
    private final VenueRepository venueRepo;
    private final ScreenRepository screenRepo;
    private final SeatRepository seatRepo;
    private final ShowSeatRepository showSeatRepo;
    private final ShowTicketRepository ticketRepo;
    private final EventShowMapper mapper;

    public ShowServiceImpl(ShowRepository s, EventRepository e, VenueRepository v, ScreenRepository sc, SeatRepository seats, ShowSeatRepository ss, ShowTicketRepository t, EventShowMapper m) {
        showRepo = s;
        eventRepo = e;
        venueRepo = v;
        screenRepo = sc;
        seatRepo = seats;
        showSeatRepo = ss;
        ticketRepo = t;
        mapper = m;
    }

    @Transactional
    public ShowResponse create(CreateShowRequest r) {
        Event e = eventRepo.findById(r.getEventId()).orElseThrow(() -> new ResourceNotFoundException("Event not found: " + r.getEventId()));
        Venue v = venueRepo.findById(r.getVenueId()).orElseThrow(() -> new ResourceNotFoundException("Venue not found: " + r.getVenueId()));
        Screen sc = screenRepo.findById(r.getScreenId()).orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + r.getScreenId()));
        if (e.getStatus() != EventStatus.ACTIVE) throw new BusinessRuleException("Only ACTIVE events can have shows");
        if (v.getStatus() != ResourceStatus.ACTIVE || sc.getStatus() != ResourceStatus.ACTIVE)
            throw new BusinessRuleException("Venue and screen must be ACTIVE");
        if (!sc.getVenue().getId().equals(v.getId())) throw new ConflictException("Screen does not belong to venue");
        if (!r.getStartTime().isAfter(Instant.now()))
            throw new IllegalArgumentException("startTime must be in the future");
        if (!r.getEndTime().isAfter(r.getStartTime()))
            throw new IllegalArgumentException("endTime must be after startTime");
        if (showRepo.existsByScreenIdAndStartTimeLessThanAndEndTimeGreaterThan(sc.getId(), r.getEndTime(), r.getStartTime()))
            throw new ConflictException("SHOW_OVERLAP: screen already has an overlapping show");
        if (!e.getLanguages().contains(r.getLanguage()))
            throw new BusinessRuleException("Language is not configured for this event");
        if (r.getTickets() == null || r.getTickets().isEmpty())
            throw new IllegalArgumentException("At least one ticket price is required");
        Show s = new Show();
        s.setEvent(e);
        s.setVenue(v);
        s.setScreen(sc);
        s.setLanguage(r.getLanguage());
        s.setStartTime(r.getStartTime());
        s.setEndTime(r.getEndTime());
        s.setShowFormat(r.getShowFormat());
        s.setStatus(ShowStatus.SCHEDULED);
        s = showRepo.save(s);
        Map<TicketCategory, BigDecimal> prices = new EnumMap<>(TicketCategory.class);
        for (var t : r.getTickets()) {
            if (t.getPrice() == null || t.getPrice().signum() < 0)
                throw new IllegalArgumentException("Ticket price cannot be negative");
            if (prices.put(t.getTicketCategory(), t.getPrice()) != null)
                throw new ConflictException("Duplicate ticket category");
            ShowTicket st = new ShowTicket();
            st.setShow(s);
            st.setTicketCategory(t.getTicketCategory());
            st.setPrice(t.getPrice());
            ticketRepo.save(st);
        }
        List<Seat> seats = seatRepo.findByScreenId(sc.getId(), Pageable.unpaged()).getContent().stream().filter(x -> x.getStatus() == ResourceStatus.ACTIVE).toList();
        if (seats.isEmpty()) throw new BusinessRuleException("Screen has no active seats");
        List<ShowSeat> ss = new ArrayList<>();
        for (Seat seat : seats) {
            ShowSeat x = new ShowSeat();
            x.setShow(s);
            x.setSeat(seat);
            x.setStatus(SeatStatus.AVAILABLE);
            x.setPrice(prices.getOrDefault(seat.getTicketCategory(), BigDecimal.ZERO));
            ss.add(x);
        }
        showSeatRepo.saveAll(ss);
        return mapper.show(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> search(Long eventId, Long venueId, String city, LocalDate date, Language language, ShowFormat format, ShowStatus status, int p, int size, String sort) {
        Page<Show> x = showRepo.search(eventId, venueId, blank(city), date, language, format, status, page(p, size, sort));
        return new PageResponse<>(x.map(mapper::show));
    }

    @Transactional(readOnly = true)
    public ShowDetailResponse get(Long id) {
        Show s = find(id);
        ShowDetailResponse r = new ShowDetailResponse();
        ShowResponse b = mapper.show(s);
        copy(b, r);
        r.setAvailableSeats(showSeatRepo.countByShowIdAndStatus(id, SeatStatus.AVAILABLE));
        r.setHeldSeats(showSeatRepo.countByShowIdAndStatus(id, SeatStatus.HELD));
        r.setBookedSeats(showSeatRepo.countByShowIdAndStatus(id, SeatStatus.BOOKED));
        r.setBlockedSeats(showSeatRepo.countByShowIdAndStatus(id, SeatStatus.BLOCKED));
        r.setTickets(tickets(id));
        return r;
    }

    @Transactional(readOnly = true)
    public List<TicketPriceResponse> tickets(Long id) {
        find(id);
        return ticketRepo.findByShowId(id).stream().map(mapper::ticket).toList();
    }

    @Transactional
    public List<TicketPriceResponse> updateTickets(Long id, UpdateTicketPricesRequest r) {
        find(id);
        if (showSeatRepo.countByShowIdAndStatus(id, SeatStatus.BOOKED) > 0)
            throw new ConflictException("Cannot change ticket prices after a seat is booked");
        Map<TicketCategory, BigDecimal> m = new EnumMap<>(TicketCategory.class);
        for (var x : r.getTickets()) {
            if (x.getPrice() == null || x.getPrice().signum() < 0)
                throw new IllegalArgumentException("Ticket price cannot be negative");
            if (m.put(x.getTicketCategory(), x.getPrice()) != null)
                throw new ConflictException("Duplicate ticket category");
        }
        List<ShowTicket> existing = ticketRepo.findByShowId(id);
        for (ShowTicket t : existing)
            if (m.containsKey(t.getTicketCategory())) t.setPrice(m.get(t.getTicketCategory()));
        ticketRepo.saveAll(existing);
        List<ShowSeat> seats = showSeatRepo.findByShowId(id, Pageable.unpaged()).getContent();
        for (ShowSeat ss : seats) {
            BigDecimal price = m.get(ss.getSeat().getTicketCategory());
            if (price != null) ss.setPrice(price);
        }
        showSeatRepo.saveAll(seats);
        return tickets(id);
    }

    @Transactional
    public ShowResponse changeStatus(Long id, ShowStatus status) {
        Show s = find(id);
        if (s.getStatus() == ShowStatus.CANCELLED || s.getStatus() == ShowStatus.COMPLETED)
            throw new ConflictException("Show is already in a terminal state");
        if (status == ShowStatus.CANCELLED) return cancel(id);
        if (!allowed(s.getStatus(), status))
            throw new ConflictException("INVALID_STATE_TRANSITION: " + s.getStatus() + " -> " + status);
        s.setStatus(status);
        return mapper.show(showRepo.save(s));
    }

    @Transactional
    public ShowResponse cancel(Long id) {
        Show s = find(id);
        if (s.getStatus() == ShowStatus.CANCELLED || s.getStatus() == ShowStatus.COMPLETED)
            throw new ConflictException("Show cannot be cancelled from state " + s.getStatus());
        if (showSeatRepo.countByShowIdAndStatus(id, SeatStatus.BOOKED) > 0)
            throw new ConflictException("Show with booked seats cannot be cancelled");
        s.setStatus(ShowStatus.CANCELLED);
        showSeatRepo.findByShowId(id, Pageable.unpaged()).getContent().forEach(x -> {
            if (x.getStatus() != SeatStatus.BOOKED) x.setStatus(SeatStatus.BLOCKED);
        });
        return mapper.show(showRepo.save(s));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> seats(Long id, int p, int size, String sort) {
        find(id);
        Page<ShowSeat> x = showSeatRepo.findByShowId(id, pageSeat(p, size, sort));
        return new PageResponse<>(x.map(mapper::seat));
    }

    @Transactional
    public SeatResponse changeSeatStatus(Long showId, Long seatId, SeatStatus status) {
        ShowSeat ss = showSeatRepo.findByShowIdAndSeatId(showId, seatId).orElseThrow(() -> new ResourceNotFoundException("Show seat not found"));
        if (ss.getStatus() == SeatStatus.BOOKED && status != SeatStatus.BOOKED)
            throw new ConflictException("Booked seat cannot be changed");
        if (status == SeatStatus.HELD || status == SeatStatus.BOOKED)
            throw new ConflictException("Admin can only block or unblock a show seat");
        ss.setStatus(status);
        return mapper.seat(showSeatRepo.save(ss));
    }

    @Transactional(readOnly = true)
    public InternalShowResponse internalShow(Long id) {
        Show s = find(id);
        InternalShowResponse r = new InternalShowResponse();
        r.setShowId(id);
        r.setStatus(s.getStatus());
        r.setEventStatus(s.getEvent().getStatus());
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setTickets(tickets(id));
        return r;
    }

    @Transactional
    public SeatActionResponse hold(Long showId, SeatHoldRequest r) {
        Show s = find(showId);
        if (s.getStatus() != ShowStatus.BOOKING_OPEN || !s.getStartTime().isAfter(Instant.now()))
            throw new BusinessRuleException("SHOW_NOT_OPEN: show is not open for booking");
        if (new HashSet<>(r.getSeatIds()).size() != r.getSeatIds().size())
            throw new IllegalArgumentException("Duplicate seat ids are not allowed");
        Instant now = Instant.now();
        Instant expiry = r.getHoldExpiresAt() == null ? now.plusSeconds(600) : r.getHoldExpiresAt();
        if (!expiry.isAfter(now)) throw new IllegalArgumentException("holdExpiresAt must be in the future");
        long alreadyHeld = showSeatRepo.countByShowIdAndSeatIdInAndHeldByBookingId(showId, r.getSeatIds(), r.getBookingId());
        int newlyHeld = showSeatRepo.holdAvailableSeats(showId, r.getSeatIds(), r.getBookingId(), expiry);
        if (alreadyHeld + newlyHeld != r.getSeatIds().size())
            throw new ConflictException("SEAT_NOT_AVAILABLE: one or more requested seats are unavailable");
        return action(showId, r.getBookingId(), r.getSeatIds(), "HELD");
    }

    @Transactional
    public SeatActionResponse confirm(Long showId, SeatBookingActionRequest r) {
        find(showId);
        List<ShowSeat> rows = showSeatRepo.findByShowIdAndHeldByBookingId(showId, r.getBookingId());
        if (rows.isEmpty()) throw new ResourceNotFoundException("No held seats found for booking");
        for (ShowSeat ss : rows) {
            if (ss.getHoldExpiresAt() != null && ss.getHoldExpiresAt().isBefore(Instant.now()))
                throw new ConflictException("HOLD_EXPIRED: seat hold expired");
            ss.setStatus(SeatStatus.BOOKED);
            ss.setHoldExpiresAt(null);
        }
        showSeatRepo.saveAll(rows);
        return action(showId, r.getBookingId(), rows.stream().map(x -> x.getSeat().getId()).toList(), "BOOKED");
    }

    @Transactional
    public SeatActionResponse release(Long showId, SeatBookingActionRequest r) {
        find(showId);
        List<ShowSeat> rows = showSeatRepo.findByShowIdAndHeldByBookingId(showId, r.getBookingId());
        for (ShowSeat ss : rows) {
            ss.setStatus(SeatStatus.AVAILABLE);
            ss.setHeldByBookingId(null);
            ss.setHoldExpiresAt(null);
        }
        showSeatRepo.saveAll(rows);
        return action(showId, r.getBookingId(), rows.stream().map(x -> x.getSeat().getId()).toList(), "AVAILABLE");
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> bookingSeats(Long bookingId) {
        return showSeatRepo.findAll().stream().filter(x -> Objects.equals(x.getHeldByBookingId(), bookingId)).map(mapper::seat).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> seatsAll(Long showId) {
        find(showId);
        return showSeatRepo.findByShowId(showId, Pageable.unpaged())
                .getContent()
                .stream()
                .map(mapper::seat)
                .toList();
    }

    private boolean allowed(ShowStatus from, ShowStatus to) {
        return (from == ShowStatus.SCHEDULED && to == ShowStatus.BOOKING_OPEN) || (from == ShowStatus.BOOKING_OPEN && to == ShowStatus.BOOKING_CLOSED) || (from == ShowStatus.BOOKING_CLOSED && to == ShowStatus.COMPLETED);
    }

    private Show find(Long id) {
        return showRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Show not found: " + id));
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Pageable page(int p, int size, String sort) {
        if (p < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        String f = sort == null || sort.isBlank() ? "startTime" : sort;
        if (!Set.of("startTime", "endTime", "status", "language").contains(f))
            throw new IllegalArgumentException("Unsupported sort field: " + f);
        return PageRequest.of(p, size, Sort.by(f));
    }

    private Pageable pageSeat(int p, int size, String sort) {
        if (p < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        String f = sort == null || sort.isBlank() ? "id" : sort;
        if (!Set.of("id", "status", "price").contains(f))
            throw new IllegalArgumentException("Unsupported sort field: " + f);
        return PageRequest.of(p, size, Sort.by(f));
    }

    private void copy(ShowResponse a, ShowDetailResponse b) {
        b.setId(a.getId());
        b.setEventId(a.getEventId());
        b.setVenueId(a.getVenueId());
        b.setScreenId(a.getScreenId());
        b.setEventTitle(a.getEventTitle());
        b.setVenueName(a.getVenueName());
        b.setCity(a.getCity());
        b.setScreenName(a.getScreenName());
        b.setLanguage(a.getLanguage());
        b.setStartTime(a.getStartTime());
        b.setEndTime(a.getEndTime());
        b.setShowFormat(a.getShowFormat());
        b.setStatus(a.getStatus());
    }

    private SeatActionResponse action(Long show, Long booking, List<Long> ids, String status) {
        SeatActionResponse r = new SeatActionResponse();
        r.setShowId(show);
        r.setBookingId(booking);
        r.setSeatIds(ids);
        r.setStatus(status);
        return r;
    }
}
