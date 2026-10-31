package com.tickethub.auth.service.impl;

import com.tickethub.auth.dto.request.UpdateUserRolesRequest;
import com.tickethub.auth.dto.response.PageResponse;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.entity.AuditAction;
import com.tickethub.auth.entity.Role;
import com.tickethub.auth.entity.User;
import com.tickethub.auth.entity.UserStatus;
import com.tickethub.auth.exception.ApiException;
import com.tickethub.auth.repository.RefreshTokenRepository;
import com.tickethub.auth.repository.RoleRepository;
import com.tickethub.auth.repository.UserRepository;
import com.tickethub.auth.service.AdminUserService;
import com.tickethub.auth.service.AuditService;
import com.tickethub.auth.util.ClientInfo;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService audit;

    public AdminUserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                                RefreshTokenRepository refreshTokenRepository, AuditService audit) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, UserStatus status, int page, int size) {
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.coalesce(root.<String>get("phone"), ""), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = userRepository.findAll(spec, pageable);
        return new PageResponse<>(result.getContent().stream().map(UserResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(load(id));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long adminId, Long id, UserStatus status, ClientInfo client) {
        if (status == UserStatus.DELETED) {
            throw ApiException.badRequest("INVALID_STATUS", "Use DELETE /admin/users/{id} to delete a user");
        }
        if (adminId.equals(id)) {
            throw ApiException.badRequest("CANNOT_MODIFY_SELF", "You cannot change your own status");
        }
        User u = load(id);
        u.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            u.setFailedLoginAttempts(0);   // activating also unlocks the account
            u.setLockedUntil(null);
        } else {
            refreshTokenRepository.revokeAllForUser(id, Instant.now());   // kick the user out of every session
        }
        audit.record(AuditAction.STATUS_CHANGED, id, client, "status=" + status + " by admin " + adminId);
        return UserResponse.from(u);
    }

    @Override
    @Transactional
    public UserResponse updateRoles(Long adminId, Long id, UpdateUserRolesRequest request, ClientInfo client) {
        Set<String> assign = names(request.assign());
        Set<String> remove = names(request.remove());
        if (assign.isEmpty() && remove.isEmpty()) {
            throw ApiException.badRequest("NO_CHANGES", "Provide at least one role in 'assign' or 'remove'");
        }
        if (!Collections.disjoint(assign, remove)) {
            throw ApiException.badRequest("CONFLICTING_ROLES", "A role cannot be both assigned and removed");
        }
        if (adminId.equals(id) && remove.contains("ADMIN")) {
            throw ApiException.badRequest("CANNOT_MODIFY_SELF", "You cannot remove your own ADMIN role");
        }

        Set<String> all = new HashSet<>(assign);
        all.addAll(remove);
        Map<String, Role> byName = roleRepository.findByNameIn(all).stream()
                .collect(Collectors.toMap(Role::getName, r -> r));
        if (byName.size() != all.size()) {
            Set<String> unknown = new TreeSet<>(all);
            unknown.removeAll(byName.keySet());
            throw ApiException.badRequest("UNKNOWN_ROLE", "Unknown role(s): " + unknown);
        }

        User u = load(id);
        assign.forEach(n -> u.getRoles().add(byName.get(n)));
        remove.forEach(n -> u.getRoles().removeIf(r -> r.getName().equals(n)));
        if (u.getRoles().isEmpty()) {
            throw ApiException.badRequest("USER_MUST_HAVE_ROLE", "A user must keep at least one role");
        }
        audit.record(AuditAction.ROLES_CHANGED, id, client, "assign=" + assign + " remove=" + remove + " by admin " + adminId);
        return UserResponse.from(u);
    }

    @Override
    @Transactional
    public void delete(Long adminId, Long id, ClientInfo client) {
        if (adminId.equals(id)) {
            throw ApiException.badRequest("CANNOT_MODIFY_SELF", "You cannot delete your own account here");
        }
        User u = load(id);
        u.setStatus(UserStatus.DELETED);   // soft delete
        refreshTokenRepository.revokeAllForUser(id, Instant.now());
        audit.record(AuditAction.ACCOUNT_DELETED, id, client, "by admin " + adminId);
    }

    private User load(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User " + id + " not found"));
    }

    private static Set<String> names(Set<String> in) {
        if (in == null) return Set.of();
        return in.stream().filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
