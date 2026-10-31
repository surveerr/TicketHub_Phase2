package com.tickethub.auth.security;

import com.tickethub.auth.config.AuthProperties;
import com.tickethub.auth.entity.Permission;
import com.tickethub.auth.entity.Role;
import com.tickethub.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Issues and validates short-lived access tokens (HS256).
 *
 * Token contract (other services rely on it):
 *   sub = user id, email, roles = ["USER", ...] (no ROLE_ prefix), permissions = ["VIEW_EVENT", ...],
 *   token_type = "access", iss, iat, exp, jti.
 */
@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(AuthProperties props) {
        byte[] secret = props.getJwt().getSecret() == null ? new byte[0] : props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = props.getJwt().getIssuer();
        this.accessTtl = Duration.ofMinutes(props.getJwt().getAccessTokenTtlMinutes());
    }

    public String generateAccessToken(User user) {
        Set<String> roles = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        for (Role r : user.getRoles()) {
            roles.add(r.getName());
            for (Permission p : r.getPermissions()) permissions.add(p.getName());
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("roles", new ArrayList<>(roles))
                .claim("permissions", new ArrayList<>(permissions))
                .claim("token_type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Empty when the token is missing, malformed, expired, wrongly signed or not an access token. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(token).getPayload();
            if (!"access".equals(c.get("token_type", String.class))) return Optional.empty();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(c.getSubject()), c.get("email", String.class),
                    toSet(c.get("roles")), toSet(c.get("permissions"))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private static Set<String> toSet(Object value) {
        if (value instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }
}
