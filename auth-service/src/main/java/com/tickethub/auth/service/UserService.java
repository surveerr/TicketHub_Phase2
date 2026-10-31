package com.tickethub.auth.service;

import com.tickethub.auth.dto.request.UpdateProfileRequest;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.util.ClientInfo;

public interface UserService {
    UserResponse getProfile(Long userId);
    UserResponse updateProfile(Long userId, UpdateProfileRequest request, ClientInfo client);
    void deleteAccount(Long userId, ClientInfo client);
}
