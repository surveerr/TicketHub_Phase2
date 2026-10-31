package com.tickethub.eventshow.service.impl;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.entity.*;
import com.tickethub.eventshow.enums.*;
import com.tickethub.eventshow.exception.*;
import com.tickethub.eventshow.mapper.EventShowMapper;
import com.tickethub.eventshow.repository.*;
import com.tickethub.eventshow.service.VenueService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VenueServiceImpl implements VenueService {
    private final LocationRepository locationRepo;
    private final VenueRepository venueRepo;
    private final ScreenRepository screenRepo;
    private final SeatRepository seatRepo;
    private final EventShowMapper mapper;

    public VenueServiceImpl(LocationRepository l, VenueRepository v, ScreenRepository s, SeatRepository seats, EventShowMapper m) {
        locationRepo = l;
        venueRepo = v;
        screenRepo = s;
        seatRepo = seats;
        mapper = m;
    }

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest r) {
        if (locationRepo.existsByCityIgnoreCaseAndStateIgnoreCaseAndCountryIgnoreCase(r.getCity(), r.getState(), r.getCountry()))
            throw new ConflictException("Location already exists");
        Location l = new Location();
        l.setCity(r.getCity());
        l.setState(r.getState());
        l.setCountry(r.getCountry());
        return mapper.location(locationRepo.save(l));
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> locations(int p, int s, String sort) {
        Page<Location> x = locationRepo.findAll(page(p, s, sort, "city", Set.of("city", "state", "country")));
        return new PageResponse<>(x.map(mapper::location));
    }

    @Transactional
    public LocationResponse updateLocation(Long id, UpdateLocationRequest r) {
        Location l = location(id);
        l.setCity(r.getCity());
        l.setState(r.getState());
        l.setCountry(r.getCountry());
        return mapper.location(locationRepo.save(l));
    }

    @Transactional
    public void deleteLocation(Long id) {
        Location l = location(id);
        if (venueRepo.search(id, null, null, Pageable.unpaged()).hasContent())
            throw new ConflictException("Location cannot be deleted while venues exist");
        locationRepo.delete(l);
    }

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest r) {
        Location l = location(r.getLocationId());
        Venue v = new Venue();
        v.setName(r.getName());
        v.setAddressLine(r.getAddressLine());
        v.setPincode(r.getPincode());
        v.setLatitude(r.getLatitude());
        v.setLongitude(r.getLongitude());
        v.setLocation(l);
        v.setStatus(ResourceStatus.ACTIVE);
        return mapper.venue(venueRepo.save(v));
    }

    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> searchVenues(Long loc, ResourceStatus status, String name, int p, int s, String sort) {
        Page<Venue> x = venueRepo.search(loc, status, blank(name), page(p, s, sort, "name", Set.of("name", "status")));
        return new PageResponse<>(x.map(mapper::venue));
    }

    @Transactional(readOnly = true)
    public VenueResponse getVenue(Long id) {
        return mapper.venue(venue(id));
    }

    @Transactional
    public VenueResponse updateVenue(Long id, UpdateVenueRequest r) {
        Venue v = venue(id);
        v.setName(r.getName());
        v.setAddressLine(r.getAddressLine());
        v.setPincode(r.getPincode());
        v.setLatitude(r.getLatitude());
        v.setLongitude(r.getLongitude());
        v.setLocation(location(r.getLocationId()));
        return mapper.venue(venueRepo.save(v));
    }

    @Transactional
    public VenueResponse changeVenueStatus(Long id, ResourceStatus status) {
        Venue v = venue(id);
        v.setStatus(status);
        return mapper.venue(venueRepo.save(v));
    }

    @Transactional
    public ScreenResponse createScreen(Long venueId, CreateScreenRequest r) {
        Venue v = venue(venueId);
        if (screenRepo.existsByVenueIdAndNameIgnoreCase(venueId, r.getName()))
            throw new ConflictException("Screen already exists");
        Screen s = new Screen();
        s.setVenue(v);
        s.setName(r.getName());
        s.setTotalSeats(r.getRows() * r.getSeatsPerRow());
        s.setStatus(ResourceStatus.ACTIVE);
        s = screenRepo.save(s);
        List<Seat> seats = new ArrayList<>();
        for (int row = 0; row < r.getRows(); row++) {
            String label = String.valueOf((char) ('A' + row));
            for (int n = 1; n <= r.getSeatsPerRow(); n++) {
                Seat seat = new Seat();
                seat.setScreen(s);
                seat.setRowLabel(label);
                seat.setSeatNumber(n);
                seat.setTicketCategory(r.getTicketCategory());
                seat.setStatus(ResourceStatus.ACTIVE);
                seats.add(seat);
            }
        }
        seatRepo.saveAll(seats);
        return mapper.screen(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScreenResponse> screens(Long venueId, int p, int s, String sort) {
        venue(venueId);
        Page<Screen> x = screenRepo.findByVenueId(venueId, page(p, s, sort, "name", Set.of("name", "status")));
        return new PageResponse<>(x.map(mapper::screen));
    }

    @Transactional(readOnly = true)
    public ScreenResponse getScreen(Long id) {
        return mapper.screen(screen(id));
    }

    @Transactional
    public ScreenResponse updateScreen(Long id, UpdateScreenRequest r) {
        Screen s = screen(id);
        s.setName(r.getName());
        return mapper.screen(screenRepo.save(s));
    }

    @Transactional
    public ScreenResponse changeScreenStatus(Long id, ResourceStatus status) {
        Screen s = screen(id);
        s.setStatus(status);
        return mapper.screen(screenRepo.save(s));
    }

    private Location location(Long id) {
        return locationRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location not found: " + id));
    }

    private Venue venue(Long id) {
        return venueRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venue not found: " + id));
    }

    private Screen screen(Long id) {
        return screenRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + id));
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Pageable page(int p, int s, String sort, String def, Set<String> allowed) {
        if (p < 0) throw new IllegalArgumentException("page must be >= 0");
        if (s < 1 || s > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        String f = sort == null || sort.isBlank() ? def : sort;
        if (!allowed.contains(f)) throw new IllegalArgumentException("Unsupported sort field: " + f);
        return PageRequest.of(p, s, Sort.by(f));
    }
}
