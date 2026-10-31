package com.tickethub.auth.controller;

import com.tickethub.auth.dto.request.*;
import com.tickethub.auth.dto.response.AuthResponse;
import com.tickethub.auth.dto.response.MessageResponse;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.security.AuthenticatedUser;
import com.tickethub.auth.service.AuthService;
import com.tickethub.auth.util.ClientInfo;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, ClientInfo.from(http)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientInfo.from(http));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), ClientInfo.from(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        authService.logout(request.refreshToken(), ClientInfo.from(http));
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void logoutAll(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest http) {
        authService.logoutAll(me.id(), ClientInfo.from(http));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        authService.forgotPassword(request.email(), ClientInfo.from(http));
        return ResponseEntity.accepted().body(new MessageResponse("If an account exists for this email, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        authService.resetPassword(request, ClientInfo.from(http));
        return new MessageResponse("Password has been reset. Please log in with your new password");
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        authService.verifyEmail(request.token(), ClientInfo.from(http));
        return new MessageResponse("Email verified");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        authService.resendVerification(request.email(), ClientInfo.from(http));
        return ResponseEntity.accepted().body(new MessageResponse("If the account exists and is not verified, a new link has been sent"));
    }

    /** Revokes all sessions and returns a fresh token pair for the caller. */
    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthenticatedUser me,
                                       @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        return authService.changePassword(me.id(), request, ClientInfo.from(http));
    }
}
