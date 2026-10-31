package com.tickethub.eventshow.service;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.*;

import java.util.List;

public interface ShowService {
    ShowResponse create(CreateShowRequest r);

    PageResponse<ShowResponse> search(Long eventId, Long venueId, String city, java.time.LocalDate date, Language language, ShowFormat format, ShowStatus status, int page, int size, String sort);

    ShowDetailResponse get(Long id);

    java.util.List<TicketPriceResponse> tickets(Long id);

    java.util.List<TicketPriceResponse> updateTickets(Long id, UpdateTicketPricesRequest r);

    ShowResponse changeStatus(Long id, ShowStatus status);

    ShowResponse cancel(Long id);

    PageResponse<SeatResponse> seats(Long showId, int page, int size, String sort);

    SeatResponse changeSeatStatus(Long showId, Long seatId, SeatStatus status);

    InternalShowResponse internalShow(Long id);

    SeatActionResponse hold(Long showId, SeatHoldRequest r);

    SeatActionResponse confirm(Long showId, SeatBookingActionRequest r);

    SeatActionResponse release(Long showId, SeatBookingActionRequest r);

    List<SeatResponse> bookingSeats(Long bookingId);
    List<SeatResponse> seatsAll(Long showId);
}
