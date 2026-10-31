package com.tickethub.eventshow.dto.request;

import com.tickethub.eventshow.enums.Language;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
public class LanguageRequest {
    @NotNull
    private Language language;
}
