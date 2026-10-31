package com.tickethub.eventshow.service.impl;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.entity.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.exception.*;
import com.tickethub.eventshow.mapper.EventShowMapper;
import com.tickethub.eventshow.repository.*;
import com.tickethub.eventshow.service.SeatService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SeatServiceImpl implements SeatService {
    private final SeatRepository seatRepo;
    private final ScreenRepository screenRepo;
    private final ShowSeatRepository showSeatRepo;
    private final EventShowMapper mapper;

    public SeatServiceImpl(SeatRepository s, ScreenRepository sc, ShowSeatRepository ss, EventShowMapper m) {
        seatRepo = s;
        screenRepo = sc;
        showSeatRepo = ss;
        mapper = m;
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> screenSeats(Long screenId, int p, int s, String sort) {
        screen(screenId);
        Page<Seat> x = seatRepo.findByScreenId(screenId, page(p, s, sort));
        Page<SeatResponse> out = x.map(seat -> {
            SeatResponse r = new SeatResponse();
            r.setId(seat.getId());
            r.setRowLabel(seat.getRowLabel());
            r.setSeatNumber(seat.getSeatNumber());
            r.setTicketCategory(seat.getTicketCategory());
            r.setStatus(seat.getStatus() == ResourceStatus.ACTIVE ? SeatStatus.AVAILABLE : SeatStatus.BLOCKED);
            return r;
        });
        return new PageResponse<>(out);
    }

    @Transactional
    public SeatResponse addSeat(Long screenId, AddSeatRequest r) {
        Screen s = screen(screenId);
        if (seatRepo.existsByScreenIdAndRowLabelIgnoreCaseAndSeatNumber(screenId, r.getRowLabel(), r.getSeatNumber()))
            throw new ConflictException("Seat already exists");
        Seat seat = new Seat();
        seat.setScreen(s);
        seat.setRowLabel(r.getRowLabel());
        seat.setSeatNumber(r.getSeatNumber());
        seat.setTicketCategory(r.getTicketCategory());
        seat.setStatus(ResourceStatus.ACTIVE);
        return basic(seatRepo.save(seat));
    }

    @Transactional
    public PageResponse<SeatResponse> addRow(Long screenId, AddSeatRowRequest r) {
        Screen s = screen(screenId);
        List<Seat> list = new ArrayList<>();
        for (int i = 1; i <= r.getSeatsPerRow(); i++) {
            if (seatRepo.existsByScreenIdAndRowLabelIgnoreCaseAndSeatNumber(screenId, r.getRowLabel(), i))
                throw new ConflictException("Seat already exists: " + r.getRowLabel() + i);
            Seat seat = new Seat();
            seat.setScreen(s);
            seat.setRowLabel(r.getRowLabel());
            seat.setSeatNumber(i);
            seat.setTicketCategory(r.getTicketCategory());
            seat.setStatus(ResourceStatus.ACTIVE);
            list.add(seat);
        }
        seatRepo.saveAll(list);
        s.setTotalSeats((int) seatRepo.countByScreenId(screenId));
        screenRepo.save(s);
        List<SeatResponse> out = list.stream().map(this::basic).toList();
        PageResponse<SeatResponse> p = new PageResponse<>();
        p.setContent(out);
        p.setPage(0);
        p.setSize(out.size());
        p.setTotalElements(out.size());
        p.setTotalPages(1);
        p.setFirst(true);
        p.setLast(true);
        return p;
    }

    @Transactional
    public SeatResponse updateSeat(Long id, UpdateSeatRequest r) {
        Seat s = seat(id);
        s.setTicketCategory(r.getTicketCategory());
        return basic(seatRepo.save(s));
    }

    @Transactional
    public SeatResponse changeSeatStatus(Long id, ResourceStatus status) {
        Seat s = seat(id);
        s.setStatus(status);
        return basic(seatRepo.save(s));
    }

    private SeatResponse basic(Seat s) {
        SeatResponse r = new SeatResponse();
        r.setId(s.getId());
        r.setRowLabel(s.getRowLabel());
        r.setSeatNumber(s.getSeatNumber());
        r.setTicketCategory(s.getTicketCategory());
        r.setStatus(s.getStatus() == ResourceStatus.ACTIVE ? SeatStatus.AVAILABLE : SeatStatus.BLOCKED);
        return r;
    }

    private Screen screen(Long id) {
        return screenRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + id));
    }

    private Seat seat(Long id) {
        return seatRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + id));
    }

    private Pageable page(int p, int s, String sort) {
        if (p < 0 || s < 1 || s > 100) throw new IllegalArgumentException("Invalid pagination");
        String f = sort == null || sort.isBlank() ? "rowLabel" : sort;
        if (!Set.of("rowLabel", "seatNumber", "ticketCategory", "status").contains(f))
            throw new IllegalArgumentException("Unsupported sort field: " + f);
        return PageRequest.of(p, s, Sort.by(f));
    }
}
