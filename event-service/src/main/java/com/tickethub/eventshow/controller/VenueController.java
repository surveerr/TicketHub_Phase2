package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class VenueController {
    private final VenueService s;

    public VenueController(VenueService s) {
        this.s = s;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/locations")
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody CreateLocationRequest r) {
        return ResponseEntity.status(201).body(s.createLocation(r));
    }

    @GetMapping("/locations")
    public PageResponse<LocationResponse> locations(@RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "20") int size, @RequestParam(value = "sort", defaultValue = "city") String sort) {
        return s.locations(page, size, sort);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/locations/{locationId}")
    public LocationResponse updateLocation(@PathVariable("locationId") Long locationId, @Valid @RequestBody UpdateLocationRequest r) {
        return s.updateLocation(locationId, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/locations/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocation(@PathVariable("locationId") Long locationId) {
        s.deleteLocation(locationId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/venues")
    public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest r) {
        return ResponseEntity.status(201).body(s.createVenue(r));
    }

    @GetMapping("/venues")
    public PageResponse<VenueResponse> venues(@RequestParam(value = "locationId", required = false) Long locationId, @RequestParam(value = "status", required = false) ResourceStatus status, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "20") int size, @RequestParam(value = "sort", defaultValue = "name") String sort) {
        return s.searchVenues(locationId, status, name, page, size, sort);
    }

    @GetMapping("/venues/{venueId}")
    public VenueResponse getVenue(@PathVariable("venueId") Long venueId) {
        return s.getVenue(venueId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/venues/{venueId}")
    public VenueResponse updateVenue(@PathVariable("venueId") Long venueId, @Valid @RequestBody UpdateVenueRequest r) {
        return s.updateVenue(venueId, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/venues/{venueId}/status")
    public VenueResponse venueStatus(@PathVariable("venueId") Long venueId, @RequestParam("status") ResourceStatus status) {
        return s.changeVenueStatus(venueId, status);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/venues/{venueId}/screens")
    public ResponseEntity<ScreenResponse> createScreen(@PathVariable("venueId") Long venueId, @Valid @RequestBody CreateScreenRequest r) {
        return ResponseEntity.status(201).body(s.createScreen(venueId, r));
    }

    @GetMapping("/venues/{venueId}/screens")
    public PageResponse<ScreenResponse> screens(@PathVariable("venueId") Long venueId, @RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "20") int size, @RequestParam(value = "sort", defaultValue = "name") String sort) {
        return s.screens(venueId, page, size, sort);
    }

    @GetMapping("/screens/{screenId}")
    public ScreenResponse getScreen(@PathVariable("screenId") Long screenId) {
        return s.getScreen(screenId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/screens/{screenId}")
    public ScreenResponse updateScreen(@PathVariable("screenId") Long screenId, @Valid @RequestBody UpdateScreenRequest r) {
        return s.updateScreen(screenId, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/screens/{screenId}/status")
    public ScreenResponse screenStatus(@PathVariable("screenId") Long screenId, @RequestParam("status") ResourceStatus status) {
        return s.changeScreenStatus(screenId, status);
    }
}
