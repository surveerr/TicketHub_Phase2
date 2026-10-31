package com.tickethub.auth.dto.response;

import com.tickethub.auth.entity.Permission;
import com.tickethub.auth.entity.Role;
import com.tickethub.auth.entity.User;
import com.tickethub.auth.entity.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        Long id, String firstName, String lastName, String email, String phone,
        UserStatus status, boolean emailVerified, Set<String> roles, Set<String> permissions,
        Instant createdAt, Instant lastLoginAt) {

    public static UserResponse from(User u) {
        Set<String> roles = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        for (Role r : u.getRoles()) {
            roles.add(r.getName());
            for (Permission p : r.getPermissions()) permissions.add(p.getName());
        }
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                u.getStatus(), u.isEmailVerified(), roles, permissions, u.getCreatedAt(), u.getLastLoginAt());
    }
}
