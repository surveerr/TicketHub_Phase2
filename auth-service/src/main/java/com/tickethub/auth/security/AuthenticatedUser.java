package com.tickethub.auth.security;

import java.util.Set;

/** The principal placed in the SecurityContext after a valid access token is presented. */
public record AuthenticatedUser(Long id, String email, Set<String> roles, Set<String> permissions) {}
