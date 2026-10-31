package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/internal/v1")
public class InternalShowController {
    private final ShowService s;

    public InternalShowController(ShowService s) {
        this.s = s;
    }

    @GetMapping("/shows/{showId}")
    public InternalShowResponse show(@PathVariable("showId") Long showId) {
        return s.internalShow(showId);
    }

    @PostMapping("/shows/{showId}/seats/hold")
    public SeatActionResponse hold(@PathVariable("showId") Long showId, @Valid @RequestBody SeatHoldRequest r) {
        return s.hold(showId, r);
    }

    @PostMapping("/shows/{showId}/seats/confirm")
    public SeatActionResponse confirm(@PathVariable("showId") Long showId, @Valid @RequestBody SeatBookingActionRequest r) {
        return s.confirm(showId, r);
    }

    @PostMapping("/shows/{showId}/seats/release")
    public SeatActionResponse release(@PathVariable("showId") Long showId, @Valid @RequestBody SeatBookingActionRequest r) {
        return s.release(showId, r);
    }

    @GetMapping("/bookings/{bookingId}/seats")
    public List<SeatResponse> bookingSeats(@PathVariable("bookingId") Long bookingId) {
        return s.bookingSeats(bookingId);
    }

    @GetMapping("/shows/{showId}/seats")
    public java.util.List<SeatResponse> seats(@PathVariable("showId") Long showId) {
        return s.seatsAll(showId);
    }

}
