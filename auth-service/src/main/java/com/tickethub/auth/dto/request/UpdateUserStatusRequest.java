package com.tickethub.auth.dto.request;

import com.tickethub.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

/** Allowed values: ACTIVE, INACTIVE, BLOCKED (use DELETE to remove a user). */
public record UpdateUserStatusRequest(@NotNull UserStatus status) {}
