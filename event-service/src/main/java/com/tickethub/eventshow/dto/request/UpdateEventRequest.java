package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateEventRequest {
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
}
