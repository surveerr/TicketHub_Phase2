package com.tickethub.eventshow.service;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.ResourceStatus;

public interface SeatService {
    PageResponse<SeatResponse> screenSeats(Long screenId, int page, int size, String sort);

    SeatResponse addSeat(Long screenId, AddSeatRequest r);

    PageResponse<SeatResponse> addRow(Long screenId, AddSeatRowRequest r);

    SeatResponse updateSeat(Long id, UpdateSeatRequest r);

    SeatResponse changeSeatStatus(Long id, ResourceStatus status);
}
