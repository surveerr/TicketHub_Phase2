package com.tickethub.eventshow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey publicKey;
    private final SecretKey internalKey;

    public JwtAuthenticationFilter(
            @Value("${security.jwt.secret}") String publicSecret,
            @Value("${security.internal-jwt-secret}") String internalSecret) {
        this.publicKey = Keys.hmacShaKeyFor(publicSecret.getBytes(StandardCharsets.UTF_8));
        this.internalKey = Keys.hmacShaKeyFor(internalSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                setAuthentication(parse(token, publicKey));
            } catch (Exception publicTokenError) {
                try {
                    setAuthentication(parse(token, internalKey));
                } catch (Exception internalTokenError) {
                    SecurityContextHolder.clearContext();
                }
            }
        }

        chain.doFilter(request, response);
    }

    private Claims parse(String token, SecretKey key) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private void setAuthentication(Claims claims) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        Object role = claims.get("role");
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + clean(role.toString())));
        }

        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection) {
            collection.forEach(r ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + clean(r.toString()))));
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject missing");
        }

        String email = claims.get("email", String.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new JwtPrincipal(subject, email), null, authorities));
    }

    private String clean(String role) {
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }

    public record JwtPrincipal(String userId, String email) {}
}
