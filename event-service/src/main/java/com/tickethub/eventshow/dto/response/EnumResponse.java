package com.tickethub.eventshow.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class EnumResponse {
    private String name;
    private String[] values;
}
