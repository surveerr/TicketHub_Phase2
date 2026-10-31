package com.tickethub.auth.dto.response;

import java.time.Instant;

/** Same shape as the error body of event-service. */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path) {}
