package com.tickethub.auth.service;

import com.tickethub.auth.dto.request.UpdateUserRolesRequest;
import com.tickethub.auth.entity.UserStatus;
import com.tickethub.auth.dto.response.PageResponse;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.util.ClientInfo;

public interface AdminUserService {
    PageResponse<UserResponse> search(String query, UserStatus status, int page, int size);
    UserResponse get(Long id);
    UserResponse updateStatus(Long adminId, Long id, UserStatus status, ClientInfo client);
    UserResponse updateRoles(Long adminId, Long id, UpdateUserRolesRequest request, ClientInfo client);
    void delete(Long adminId, Long id, ClientInfo client);
}
