package com.tickethub.eventshow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminLoginResponse {
    private String username;
    private String role;
    private String token;
    private long expiresInMs;
}
