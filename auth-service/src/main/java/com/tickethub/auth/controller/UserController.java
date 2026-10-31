package com.tickethub.auth.controller;

import com.tickethub.auth.dto.request.UpdateProfileRequest;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.security.AuthenticatedUser;
import com.tickethub.auth.service.UserService;
import com.tickethub.auth.util.ClientInfo;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return userService.getProfile(me.id());
    }

    @PutMapping
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                               @Valid @RequestBody UpdateProfileRequest request, HttpServletRequest http) {
        return userService.updateProfile(me.id(), request, ClientInfo.from(http));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest http) {
        userService.deleteAccount(me.id(), ClientInfo.from(http));
    }
}
