package com.tickethub.eventshow.service.impl;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.entity.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.exception.*;
import com.tickethub.eventshow.mapper.EventShowMapper;
import com.tickethub.eventshow.repository.*;
import com.tickethub.eventshow.service.EventService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class EventServiceImpl implements EventService {
    private final EventRepository repo;
    private final EventShowMapper mapper;
    private final ShowRepository showRepo;

    public EventServiceImpl(EventRepository repo, EventShowMapper mapper, ShowRepository showRepo) {
        this.repo = repo;
        this.mapper = mapper;
        this.showRepo = showRepo;
    }

    @Transactional
    public EventResponse create(CreateEventRequest r) {
        Event e = new Event();
        e.setTitle(r.getTitle());
        e.setDescription(r.getDescription());
        e.setEventType(r.getEventType());
        e.setCategory(r.getCategory());
        e.setDurationMinutes(r.getDurationMinutes());
        e.setAgeRating(r.getAgeRating());
        e.setPosterUrl(r.getPosterUrl());
        e.setReleaseDate(r.getReleaseDate());
        e.setLanguages(new HashSet<>(r.getLanguages()));
        e.setStatus(EventStatus.DRAFT);
        return mapper.event(repo.save(e));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> search(String city, EventType type, Category category, Language language, EventStatus status, String q, int page, int size, String sort) {
        EventStatus effective = status == null ? EventStatus.ACTIVE : status;
        Page<Event> p = repo.search(blank(city), language, effective, type, category, blank(q), safePage(page, size, sort, "title"));
        return new PageResponse<>(p.map(mapper::event));
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id) {
        return mapper.event(repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id)));
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest r) {
        Event e = find(id);
        e.setTitle(r.getTitle());
        e.setDescription(r.getDescription());
        e.setEventType(r.getEventType());
        e.setCategory(r.getCategory());
        e.setDurationMinutes(r.getDurationMinutes());
        e.setAgeRating(r.getAgeRating());
        e.setPosterUrl(r.getPosterUrl());
        e.setReleaseDate(r.getReleaseDate());
        return mapper.event(repo.save(e));
    }

    @Transactional
    public EventResponse changeStatus(Long id, EventStatus status) {
        Event e = find(id);
        EventStatus current = e.getStatus();
        if (status == EventStatus.DRAFT || current == EventStatus.ARCHIVED)
            throw new ConflictException("INVALID_STATE_TRANSITION: event status cannot move to " + status);
        if (status == EventStatus.INACTIVE && showRepo.findByEventId(id).stream().anyMatch(s -> s.getStartTime().isAfter(java.time.Instant.now()) && s.getStatus() == ShowStatus.BOOKING_OPEN))
            throw new ConflictException("Event has future shows open for booking");
        if (current == EventStatus.DRAFT && status != EventStatus.ACTIVE || current == EventStatus.ACTIVE && status != EventStatus.INACTIVE || current == EventStatus.INACTIVE && status != EventStatus.ACTIVE && status != EventStatus.ARCHIVED)
            throw new ConflictException("INVALID_STATE_TRANSITION: " + current + " -> " + status);
        e.setStatus(status);
        return mapper.event(repo.save(e));
    }

    @Transactional
    public EventResponse addLanguage(Long id, LanguageRequest r) {
        Event e = find(id);
        if (!e.getLanguages().add(r.getLanguage())) throw new ConflictException("Language already exists for event");
        return mapper.event(repo.save(e));
    }

    @Transactional
    public void removeLanguage(Long id, Language language) {
        Event e = find(id);
        if (!e.getLanguages().contains(language)) throw new ResourceNotFoundException("Language not found on event");
        boolean used = showRepo.findByEventId(id).stream().anyMatch(s -> s.getStartTime().isAfter(java.time.Instant.now()) && s.getLanguage() == language && s.getStatus() != ShowStatus.CANCELLED);
        if (used) throw new ConflictException("Language is used by a future show");
        e.getLanguages().remove(language);
        repo.save(e);
    }

    private Event find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Pageable safePage(int p, int s, String sort, String def) {
        if (p < 0) throw new IllegalArgumentException("page must be >= 0");
        if (s < 1 || s > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        String f = sort == null || sort.isBlank() ? def : sort;
        Set<String> allowed = Set.of("title", "releaseDate", "durationMinutes");
        if (!allowed.contains(f)) throw new IllegalArgumentException("Unsupported sort field: " + f);
        return PageRequest.of(p, s, Sort.by(f));
    }
}
