package com.tickethub.eventshow.service;

import com.tickethub.eventshow.dto.request.*;
import com.tickethub.eventshow.dto.response.*;
import com.tickethub.eventshow.enums.*;
import org.springframework.data.domain.*;

public interface EventService {
    EventResponse create(CreateEventRequest r);

    PageResponse<EventResponse> search(String city, EventType type, Category category, Language language, EventStatus status, String q, int page, int size, String sort);

    EventResponse get(Long id);

    EventResponse update(Long id, UpdateEventRequest r);

    EventResponse changeStatus(Long id, EventStatus status);

    EventResponse addLanguage(Long id, LanguageRequest r);

    void removeLanguage(Long id, Language language);
}
