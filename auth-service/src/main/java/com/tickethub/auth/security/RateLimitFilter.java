package com.tickethub.auth.security;

import com.tickethub.auth.config.AuthProperties;
import com.tickethub.auth.util.ClientInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory fixed-window limiter per (client IP, endpoint) for the abuse-prone auth endpoints.
 * It is per instance: with several auth-service instances, enforce the limit at the gateway as well.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password", "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification");

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();
        Window(long start) { this.start = start; }
    }

    private final AuthProperties.RateLimit config;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AuthProperties.RateLimit config) {
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.isEnabled() || !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long windowMs = config.getWindowSeconds() * 1000;
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> now - w.start >= windowMs);
        }
        String key = ClientInfo.resolveIp(request) + "|" + request.getRequestURI();
        Window w = windows.compute(key, (k, old) -> (old == null || now - old.start >= windowMs) ? new Window(now) : old);
        if (w.count.incrementAndGet() > config.getMaxRequests()) {
            long retryAfter = Math.max(1, (w.start + windowMs - now + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            JsonErrorWriter.write(response, 429, "RATE_LIMITED", "Too many requests. Please try again later.", request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }
}
