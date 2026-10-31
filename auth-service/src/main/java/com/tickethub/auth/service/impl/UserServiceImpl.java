package com.tickethub.auth.service.impl;

import com.tickethub.auth.dto.request.UpdateProfileRequest;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.entity.AuditAction;
import com.tickethub.auth.entity.User;
import com.tickethub.auth.entity.UserStatus;
import com.tickethub.auth.exception.ApiException;
import com.tickethub.auth.repository.RefreshTokenRepository;
import com.tickethub.auth.repository.UserRepository;
import com.tickethub.auth.service.AuditService;
import com.tickethub.auth.service.UserService;
import com.tickethub.auth.util.ClientInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService audit;

    public UserServiceImpl(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository, AuditService audit) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(load(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest r, ClientInfo client) {
        User u = load(userId);
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setPhone(r.phone());
        audit.record(AuditAction.PROFILE_UPDATED, userId, client, null);
        return UserResponse.from(u);
    }

    /** Soft delete: the row stays (bookings/payments keep referring to the user id), but the account can no longer log in. */
    @Override
    @Transactional
    public void deleteAccount(Long userId, ClientInfo client) {
        User u = load(userId);
        u.setStatus(UserStatus.DELETED);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        audit.record(AuditAction.ACCOUNT_DELETED, userId, client, "self-service");
    }

    private User load(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (u.getStatus() == UserStatus.DELETED) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Account no longer exists");
        }
        return u;
    }
}
