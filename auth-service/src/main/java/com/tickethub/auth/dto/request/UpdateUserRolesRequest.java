package com.tickethub.auth.dto.request;

import java.util.Set;

/** Roles to add and/or remove in one call, e.g. {"assign":["EVENT_MANAGER"],"remove":[]}. */
public record UpdateUserRolesRequest(Set<String> assign, Set<String> remove) {}
