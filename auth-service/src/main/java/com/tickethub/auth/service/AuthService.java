package com.tickethub.auth.service;

import com.tickethub.auth.dto.request.*;
import com.tickethub.auth.dto.response.AuthResponse;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.util.ClientInfo;

public interface AuthService {
    UserResponse register(RegisterRequest request, ClientInfo client);
    AuthResponse login(LoginRequest request, ClientInfo client);
    AuthResponse refresh(String refreshToken, ClientInfo client);
    void logout(String refreshToken, ClientInfo client);
    void logoutAll(Long userId, ClientInfo client);
    void forgotPassword(String email, ClientInfo client);
    void resetPassword(ResetPasswordRequest request, ClientInfo client);
    void verifyEmail(String token, ClientInfo client);
    void resendVerification(String email, ClientInfo client);
    AuthResponse changePassword(Long userId, ChangePasswordRequest request, ClientInfo client);
}
