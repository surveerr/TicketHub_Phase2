package com.tickethub.eventshow.mapper;

import com.tickethub.eventshow.entity.*;
import com.tickethub.eventshow.dto.response.*;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class EventShowMapper {
    public EventResponse event(Event e) {
        EventResponse r = new EventResponse();
        r.setId(e.getId());
        r.setTitle(e.getTitle());
        r.setDescription(e.getDescription());
        r.setEventType(e.getEventType());
        r.setCategory(e.getCategory());
        r.setDurationMinutes(e.getDurationMinutes());
        r.setAgeRating(e.getAgeRating());
        r.setPosterUrl(e.getPosterUrl());
        r.setReleaseDate(e.getReleaseDate());
        r.setStatus(e.getStatus());
        r.setLanguages(new HashSet<>(e.getLanguages()));
        return r;
    }

    public LocationResponse location(Location e) {
        LocationResponse r = new LocationResponse();
        r.setId(e.getId());
        r.setCity(e.getCity());
        r.setState(e.getState());
        r.setCountry(e.getCountry());
        return r;
    }

    public VenueResponse venue(Venue v) {
        VenueResponse r = new VenueResponse();
        r.setId(v.getId());
        r.setLocationId(v.getLocation().getId());
        r.setCity(v.getLocation().getCity());
        r.setName(v.getName());
        r.setAddressLine(v.getAddressLine());
        r.setPincode(v.getPincode());
        r.setLatitude(v.getLatitude());
        r.setLongitude(v.getLongitude());
        r.setStatus(v.getStatus());
        return r;
    }

    public ScreenResponse screen(Screen s) {
        ScreenResponse r = new ScreenResponse();
        r.setId(s.getId());
        r.setVenueId(s.getVenue().getId());
        r.setName(s.getName());
        r.setTotalSeats(s.getTotalSeats());
        r.setStatus(s.getStatus());
        return r;
    }

    public ShowResponse show(Show s) {
        ShowResponse r = new ShowResponse();
        r.setId(s.getId());
        r.setEventId(s.getEvent().getId());
        r.setVenueId(s.getVenue().getId());
        r.setScreenId(s.getScreen().getId());
        r.setEventTitle(s.getEvent().getTitle());
        r.setVenueName(s.getVenue().getName());
        r.setCity(s.getVenue().getLocation().getCity());
        r.setScreenName(s.getScreen().getName());
        r.setLanguage(s.getLanguage());
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setShowFormat(s.getShowFormat());
        r.setStatus(s.getStatus());
        return r;
    }

    public SeatResponse seat(ShowSeat ss) {
        SeatResponse r = new SeatResponse();
        r.setId(ss.getSeat().getId());
        r.setShowSeatId(ss.getId());
        r.setRowLabel(ss.getSeat().getRowLabel());
        r.setSeatNumber(ss.getSeat().getSeatNumber());
        r.setTicketCategory(ss.getSeat().getTicketCategory());
        r.setStatus(ss.getStatus());
        r.setPrice(ss.getPrice());
        return r;
    }

    public TicketPriceResponse ticket(ShowTicket t) {
        TicketPriceResponse r = new TicketPriceResponse();
        r.setTicketCategory(t.getTicketCategory());
        r.setPrice(t.getPrice());
        return r;
    }
}
