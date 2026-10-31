package com.tickethub.eventshow.service;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.ResourceStatus;

public interface VenueService {
    LocationResponse createLocation(CreateLocationRequest r);

    PageResponse<LocationResponse> locations(int page, int size, String sort);

    LocationResponse updateLocation(Long id, UpdateLocationRequest r);

    void deleteLocation(Long id);

    VenueResponse createVenue(CreateVenueRequest r);

    PageResponse<VenueResponse> searchVenues(Long locationId, ResourceStatus status, String name, int page, int size, String sort);

    VenueResponse getVenue(Long id);

    VenueResponse updateVenue(Long id, UpdateVenueRequest r);

    VenueResponse changeVenueStatus(Long id, ResourceStatus status);

    ScreenResponse createScreen(Long venueId, CreateScreenRequest r);

    PageResponse<ScreenResponse> screens(Long venueId, int page, int size, String sort);

    ScreenResponse getScreen(Long id);

    ScreenResponse updateScreen(Long id, UpdateScreenRequest r);

    ScreenResponse changeScreenStatus(Long id, ResourceStatus status);
}
