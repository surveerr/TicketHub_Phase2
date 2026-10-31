package com.tickethub.eventshow.dto.response;

import com.tickethub.eventshow.enums.*;
import lombok.*;

import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private EventType eventType;
    private Category category;
    private Integer durationMinutes;
    private AgeRating ageRating;
    private String posterUrl;
    private LocalDate releaseDate;
    private EventStatus status;
    private Set<Language> languages;
}
