package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SeatController {
    private final SeatService s;

    public SeatController(SeatService s) {
        this.s = s;
    }

    @GetMapping("/screens/{screenId}/seats")
    public PageResponse<SeatResponse> seats(@PathVariable("screenId") Long screenId, @RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "50") int size, @RequestParam(value = "sort", defaultValue = "rowLabel") String sort) {
        return s.screenSeats(screenId, page, size, sort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/screens/{screenId}/seats")
    public ResponseEntity<SeatResponse> addSeat(@PathVariable("screenId") Long screenId, @Valid @RequestBody AddSeatRequest r) {
        return ResponseEntity.status(201).body(s.addSeat(screenId, r));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/screens/{screenId}/seats/rows")
    public ResponseEntity<PageResponse<SeatResponse>> addRow(@PathVariable("screenId") Long screenId, @Valid @RequestBody AddSeatRowRequest r) {
        return ResponseEntity.status(201).body(s.addRow(screenId, r));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/seats/{seatId}")
    public SeatResponse update(@PathVariable("seatId") Long seatId, @Valid @RequestBody UpdateSeatRequest r) {
        return s.updateSeat(seatId, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/seats/{seatId}/status")
    public SeatResponse status(@PathVariable("seatId") Long seatId, @RequestParam("status") ResourceStatus status) {
        return s.changeSeatStatus(seatId, status);
    }
}
