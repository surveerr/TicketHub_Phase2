package com.tickethub.auth.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Writes the standard error body from servlet filters / security handlers (outside Spring MVC). */
public final class JsonErrorWriter {
    private JsonErrorWriter() {}

    public static void write(HttpServletResponse res, int status, String code, String message, String path) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status
                + ",\"code\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\",\"path\":\"" + esc(path) + "\"}";
        res.getWriter().write(json);
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }
}
