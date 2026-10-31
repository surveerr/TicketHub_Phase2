package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.request.AdminLoginRequest;
import com.tickethub.eventshow.dto.response.AdminLoginResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@RestController
@RequestMapping("/api/v1/auth")
public class AdminAuthController {

    private final String adminUsername;
    private final String adminPassword;
    private final SecretKey jwtKey;
    private final long jwtExpirationMs;

    public AdminAuthController(
            @Value("${security.admin.username}") String adminUsername,
            @Value("${security.admin.password}") String adminPassword,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${security.jwt.expiration-ms:3600000}") long jwtExpirationMs) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        if (!adminUsername.equals(request.getUsername()) || !adminPassword.equals(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        String token = Jwts.builder()
                .subject(adminUsername)
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(jwtKey)
                .compact();

        return ResponseEntity.ok(
                new AdminLoginResponse(adminUsername, "ADMIN", token, jwtExpirationMs)
        );
    }
}
