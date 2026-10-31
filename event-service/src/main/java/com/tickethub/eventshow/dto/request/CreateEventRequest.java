package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class CreateEventRequest {
    @NotBlank
    @Size(max = 200)
    private String title;
    private String description;
    @NotNull
    private EventType eventType;
    @NotNull
    private Category category;
    @NotNull
    @Positive
    private Integer durationMinutes;
    private AgeRating ageRating;
    private String posterUrl;
    private LocalDate releaseDate;
    @NotEmpty
    private Set<Language> languages;
}
