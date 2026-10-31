package com.tickethub.auth.service.impl;

import com.tickethub.auth.config.AuthProperties;
import com.tickethub.auth.dto.request.*;
import com.tickethub.auth.dto.response.AuthResponse;
import com.tickethub.auth.dto.response.UserResponse;
import com.tickethub.auth.entity.*;
import com.tickethub.auth.exception.ApiException;
import com.tickethub.auth.repository.*;
import com.tickethub.auth.security.JwtService;
import com.tickethub.auth.service.AuditService;
import com.tickethub.auth.service.AuthService;
import com.tickethub.auth.service.EmailService;
import com.tickethub.auth.util.ClientInfo;
import com.tickethub.auth.util.TokenUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditService audit;
    private final AuthProperties props;
    /** Compared against when the email is unknown, so "no such user" and "wrong password" take the same time. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordResetTokenRepository resetTokenRepository,
                           EmailVerificationTokenRepository verificationTokenRepository,
                           PasswordEncoder passwordEncoder, JwtService jwtService, EmailService emailService,
                           AuditService audit, AuthProperties props) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.audit = audit;
        this.props = props;
        this.dummyHash = passwordEncoder.encode(TokenUtil.generateToken());
    }

    // ------------------------------------------------------------------ register

    @Override
    @Transactional
    public UserResponse register(RegisterRequest r, ClientInfo client) {
        String email = normalize(r.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
        }
        Role role = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role USER is missing"));

        User u = new User();
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setEmail(email);
        u.setPhone(r.phone());
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(false);
        u.setPasswordChangedAt(Instant.now());
        u.setRoles(new HashSet<>());
        u.getRoles().add(role);
        try {
            userRepository.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {   // two concurrent registrations with the same email
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
        }
        sendVerificationEmail(u, client);
        audit.record(AuditAction.REGISTER, u.getId(), client, null);
        return UserResponse.from(u);
    }

    // ------------------------------------------------------------------ login

    /**
     * noRollbackFor: failed-attempt counters and lockouts must be committed even though we throw afterwards.
     */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest r, ClientInfo client) {
        String email = normalize(r.email());
        User u = userRepository.findByEmail(email).orElse(null);
        if (u == null || u.getStatus() == UserStatus.DELETED) {
            passwordEncoder.matches(r.password(), dummyHash);
            audit.record(AuditAction.LOGIN_FAILED, u == null ? null : u.getId(), client, "unknown or deleted account");
            throw invalidCredentials();
        }

        Instant now = Instant.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            audit.record(AuditAction.LOGIN_FAILED, u.getId(), client, "account locked");
            throw ApiException.locked("ACCOUNT_LOCKED", "Account is temporarily locked after too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(r.password(), u.getPasswordHash())) {
            int attempts = u.getFailedLoginAttempts() + 1;
            u.setFailedLoginAttempts(attempts);
            audit.record(AuditAction.LOGIN_FAILED, u.getId(), client, "bad password, attempt " + attempts);
            if (attempts >= props.getSecurity().getMaxFailedAttempts()) {
                u.setLockedUntil(now.plus(Duration.ofMinutes(props.getSecurity().getLockDurationMinutes())));
                u.setFailedLoginAttempts(0);
                audit.record(AuditAction.ACCOUNT_LOCKED, u.getId(), client, null);
            }
            throw invalidCredentials();
        }

        // Password is correct: only now reveal the account state (so strangers cannot probe it).
        if (u.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.forbidden("ACCOUNT_BLOCKED", "This account has been blocked");
        }
        if (u.getStatus() == UserStatus.INACTIVE) {
            throw ApiException.forbidden("ACCOUNT_INACTIVE", "This account is deactivated");
        }
        if (props.getSecurity().isRequireEmailVerification() && !u.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email address before logging in");
        }

        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        AuthResponse response = issueTokens(u, r.deviceInfo(), client);
        audit.record(AuditAction.LOGIN_SUCCESS, u.getId(), client, null);
        return response;
    }

    // ------------------------------------------------------------------ refresh / logout

    /** Refresh-token rotation: every use revokes the presented token and issues a new pair. */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken, ClientInfo client) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenUtil.sha256(rawToken))
                .orElseThrow(AuthServiceImpl::invalidRefreshToken);
        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            // A revoked token came back: it was probably stolen. Kill every session of this user.
            refreshTokenRepository.revokeAllForUser(token.getUserId(), now);
            audit.record(AuditAction.TOKEN_REUSE_DETECTED, token.getUserId(), client, "revoked refresh token was reused");
            throw invalidRefreshToken();
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw invalidRefreshToken();
        }
        User u = userRepository.findById(token.getUserId()).orElseThrow(AuthServiceImpl::invalidRefreshToken);
        if (u.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(u.getId(), now);
            throw invalidRefreshToken();
        }

        token.setRevokedAt(now);
        AuthResponse response = issueTokens(u, token.getDeviceInfo(), client);   // roles/permissions are reloaded here
        audit.record(AuditAction.TOKEN_REFRESH, u.getId(), client, null);
        return response;
    }

    @Override
    @Transactional
    public void logout(String rawToken, ClientInfo client) {
        refreshTokenRepository.findByTokenHash(TokenUtil.sha256(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                audit.record(AuditAction.LOGOUT, t.getUserId(), client, null);
            }
        });   // unknown token: still succeed, so the endpoint reveals nothing
    }

    @Override
    @Transactional
    public void logoutAll(Long userId, ClientInfo client) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        audit.record(AuditAction.LOGOUT_ALL, userId, client, null);
    }

    // ------------------------------------------------------------------ password reset

    @Override
    @Transactional
    public void forgotPassword(String email, ClientInfo client) {
        // Always behaves the same from the caller's point of view (the controller answers 202 either way).
        userRepository.findByEmail(normalize(email)).filter(u -> u.getStatus() == UserStatus.ACTIVE).ifPresent(u -> {
            Instant now = Instant.now();
            resetTokenRepository.invalidateAllForUser(u.getId(), now);
            String raw = TokenUtil.generateToken();
            PasswordResetToken t = new PasswordResetToken();
            t.setUserId(u.getId());
            t.setTokenHash(TokenUtil.sha256(raw));
            t.setCreatedAt(now);
            t.setExpiresAt(now.plus(Duration.ofMinutes(props.getSecurity().getPasswordResetTtlMinutes())));
            resetTokenRepository.save(t);
            emailService.sendPasswordResetEmail(u, raw);
            audit.record(AuditAction.PASSWORD_RESET_REQUESTED, u.getId(), client, null);
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest r, ClientInfo client) {
        Instant now = Instant.now();
        PasswordResetToken t = resetTokenRepository.findByTokenHash(TokenUtil.sha256(r.token()))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "The reset link is invalid or has expired"));
        User u = userRepository.findById(t.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "The reset link is invalid or has expired"));

        u.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        u.setPasswordChangedAt(now);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        t.setUsedAt(now);
        refreshTokenRepository.revokeAllForUser(u.getId(), now);   // log out everywhere
        audit.record(AuditAction.PASSWORD_RESET, u.getId(), client, null);
    }

    // ------------------------------------------------------------------ email verification

    @Override
    @Transactional
    public void verifyEmail(String rawToken, ClientInfo client) {
        Instant now = Instant.now();
        EmailVerificationToken t = verificationTokenRepository.findByTokenHash(TokenUtil.sha256(rawToken))
                .filter(x -> x.getUsedAt() == null && x.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "The verification link is invalid or has expired"));
        User u = userRepository.findById(t.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "The verification link is invalid or has expired"));
        u.setEmailVerified(true);
        t.setUsedAt(now);
        audit.record(AuditAction.EMAIL_VERIFIED, u.getId(), client, null);
    }

    @Override
    @Transactional
    public void resendVerification(String email, ClientInfo client) {
        userRepository.findByEmail(normalize(email))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.isEmailVerified())
                .ifPresent(u -> sendVerificationEmail(u, client));
    }

    // ------------------------------------------------------------------ change password

    @Override
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest r, ClientInfo client) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Account no longer exists"));
        if (!passwordEncoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(r.newPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "New password must be different from the current one");
        }
        Instant now = Instant.now();
        u.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        u.setPasswordChangedAt(now);
        refreshTokenRepository.revokeAllForUser(u.getId(), now);   // every other device must log in again
        audit.record(AuditAction.PASSWORD_CHANGED, u.getId(), client, null);
        return issueTokens(u, null, client);                       // fresh session for the device that changed it
    }

    // ------------------------------------------------------------------ helpers

    private AuthResponse issueTokens(User u, String deviceInfo, ClientInfo client) {
        String device = (deviceInfo == null || deviceInfo.isBlank()) ? client.userAgent() : deviceInfo;
        if (device != null && device.length() > 255) device = device.substring(0, 255);

        long refreshDays = props.getJwt().getRefreshTokenTtlDays();
        String rawRefresh = TokenUtil.generateToken();
        Instant now = Instant.now();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(TokenUtil.sha256(rawRefresh));      // only the hash is stored
        rt.setCreatedAt(now);
        rt.setExpiresAt(now.plus(Duration.ofDays(refreshDays)));
        rt.setDeviceInfo(device);
        refreshTokenRepository.save(rt);

        return new AuthResponse(jwtService.generateAccessToken(u), rawRefresh, "Bearer",
                jwtService.accessTokenTtlSeconds(), Duration.ofDays(refreshDays).toSeconds(), UserResponse.from(u));
    }

    private void sendVerificationEmail(User u, ClientInfo client) {
        Instant now = Instant.now();
        verificationTokenRepository.invalidateAllForUser(u.getId(), now);
        String raw = TokenUtil.generateToken();
        EmailVerificationToken t = new EmailVerificationToken();
        t.setUserId(u.getId());
        t.setTokenHash(TokenUtil.sha256(raw));
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(Duration.ofHours(props.getSecurity().getEmailVerificationTtlHours())));
        verificationTokenRepository.save(t);
        emailService.sendVerificationEmail(u, raw);
        audit.record(AuditAction.EMAIL_VERIFICATION_SENT, u.getId(), client, null);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
    }

    private static ApiException invalidRefreshToken() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired or revoked");
    }
}
