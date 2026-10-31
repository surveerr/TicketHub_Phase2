package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService s;

    public EventController(EventService s) {
        this.s = s;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(s.create(r));
    }

    @GetMapping
    public PageResponse<EventResponse> search(@RequestParam(value = "city", required = false) String city, @RequestParam(value = "type", required = false) EventType type, @RequestParam(value = "category", required = false) Category category, @RequestParam(value = "language", required = false) Language language, @RequestParam(value = "status", required = false) EventStatus status, @RequestParam(value = "q", required = false) String q, @RequestParam(value = "page", defaultValue = "0") int page, @RequestParam(value = "size", defaultValue = "20") int size, @RequestParam(value = "sort", defaultValue = "title") String sort) {
        return s.search(city, type, category, language, status, q, page, size, sort);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable("eventId") Long eventId) {
        return s.get(eventId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{eventId}")
    public EventResponse update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventRequest r) {
        return s.update(eventId, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{eventId}/status")
    public EventResponse status(@PathVariable("eventId") Long eventId, @RequestParam("status") EventStatus status) {
        return s.changeStatus(eventId, status);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{eventId}/languages")
    public ResponseEntity<EventResponse> addLanguage(@PathVariable("eventId") Long eventId, @Valid @RequestBody LanguageRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(s.addLanguage(eventId, r));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{eventId}/languages/{language}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLanguage(@PathVariable("eventId") Long eventId, @PathVariable("language") Language language) {
        s.removeLanguage(eventId, language);
    }
}
