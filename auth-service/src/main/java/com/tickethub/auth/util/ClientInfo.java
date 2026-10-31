package com.tickethub.auth.util;

import jakarta.servlet.http.HttpServletRequest;

/** Who is calling: used for audit logs and as the default device description of a refresh token. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) ua = ua.substring(0, 255);
        return new ClientInfo(resolveIp(request), ua);
    }

    /** First X-Forwarded-For hop (set by the gateway) or the socket address. */
    public static String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
