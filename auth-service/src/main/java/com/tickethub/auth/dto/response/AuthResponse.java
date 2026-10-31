package com.tickethub.auth.dto.response;

/** expiresIn / refreshExpiresIn are in seconds. */
public record AuthResponse(
        String accessToken, String refreshToken, String tokenType,
        long expiresIn, long refreshExpiresIn, UserResponse user) {}
