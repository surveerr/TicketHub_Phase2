package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.response.EnumResponse;
import com.tickethub.eventshow.enums.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enums")
public class EnumController {
    @GetMapping
    public List<EnumResponse> enums() {
        return List.of(
                new EnumResponse("eventTypes", names(EventType.values())),
                new EnumResponse("categories", names(Category.values())),
                new EnumResponse("languages", names(Language.values())),
                new EnumResponse("ticketCategories", names(TicketCategory.values())),
                new EnumResponse("ageRatings", names(AgeRating.values())),
                new EnumResponse("eventStatuses", names(EventStatus.values())),
                new EnumResponse("resourceStatuses", names(ResourceStatus.values())),
                new EnumResponse("showFormats", names(ShowFormat.values())),
                new EnumResponse("showStatuses", names(ShowStatus.values())),
                new EnumResponse("seatStatuses", names(SeatStatus.values()))
        );
    }

    private String[] names(Enum<?>[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].name();
        return result;
    }
}
