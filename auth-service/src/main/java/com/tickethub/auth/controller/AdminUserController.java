package com.tickethub.auth.controller;

import com.tickethub.auth.dto.request.UpdateUserRolesRequest;
import com.tickethub.auth.dto.request.UpdateUserStatusRequest;
import com.tickethub.auth.dto.response.PageResponse;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.entity.UserStatus;
import com.tickethub.auth.security.AuthenticatedUser;
import com.tickethub.auth.service.AdminUserService;
import com.tickethub.auth.util.ClientInfo;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_USERS')")
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) UserStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return adminUserService.search(q, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_USERS')")
    public UserResponse get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('BLOCK_USER')")
    public UserResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                     @Valid @RequestBody UpdateUserStatusRequest request, HttpServletRequest http) {
        return adminUserService.updateStatus(admin.id(), id, request.status(), ClientInfo.from(http));
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ASSIGN_ROLE')")
    public UserResponse updateRoles(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                    @Valid @RequestBody UpdateUserRolesRequest request, HttpServletRequest http) {
        return adminUserService.updateRoles(admin.id(), id, request, ClientInfo.from(http));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DELETE_USER')")
    public void delete(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id, HttpServletRequest http) {
        adminUserService.delete(admin.id(), id, ClientInfo.from(http));
    }
}
