package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/shows")
public class ShowController {
    private final ShowService s;

    public ShowController(ShowService s) {
        this.s = s;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody CreateShowRequest r) {
        return ResponseEntity.status(201).body(s.create(r));
    }

    @GetMapping
    public PageResponse<ShowResponse> search(@RequestParam(value = "eventId", required = false) Long eventId, @RequestParam(value = "venueId", required = false) Long venueId, @RequestParam(value = "city", required = false) String city, @RequestParam(value = "date", required = false) LocalDate date, @RequestParam(value = "language", required = false) Language language, @RequestParam(value = "format", required = false) ShowFormat format, @RequestParam(value = "status", required = false) ShowStatus status, @RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "20") int size, @RequestParam(value = "sort", defaultValue = "startTime") String sort) {
        return s.search(eventId, venueId, city, date, language, format, status, page, size, sort);
    }

    @GetMapping("/{showId}")
    public ShowDetailResponse get(@PathVariable("showId") Long showId) {
        return s.get(showId);
    }

    @GetMapping("/{showId}/tickets")
    public List<TicketPriceResponse> tickets(@PathVariable("showId") Long showId) {
        return s.tickets(showId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{showId}/tickets")
    public List<TicketPriceResponse> updateTickets(@PathVariable("showId") Long showId, @Valid @RequestBody UpdateTicketPricesRequest r) {
        return s.updateTickets(showId, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{showId}/status")
    public ShowResponse status(@PathVariable("showId") Long showId, @RequestParam("status") ShowStatus status) {
        return s.changeStatus(showId, status);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{showId}/cancel")
    public ShowResponse cancel(@PathVariable("showId") Long showId) {
        return s.cancel(showId);
    }

    @GetMapping("/{showId}/seats")
    public PageResponse<SeatResponse> seats(@PathVariable("showId") Long showId, @RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "50") int size, @RequestParam(value = "sort", defaultValue = "id") String sort) {
        return s.seats(showId, page, size, sort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{showId}/seats/{seatId}/status")
    public SeatResponse seatStatus(@PathVariable("showId") Long showId, @PathVariable("seatId") Long seatId, @RequestParam("status") SeatStatus status) {
        return s.changeSeatStatus(showId, seatId, status);
    }
}
