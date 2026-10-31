package com.tickethub.paymentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String BOOKING_ID_HEADER = "X-Booking-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_BOOKING_KEY = "bookingId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        String bookingId = request.getHeader(BOOKING_ID_HEADER);
        if (bookingId == null || bookingId.trim().isEmpty()) {
            String param = request.getParameter("bookingId");
            if (param != null && !param.trim().isEmpty()) {
                bookingId = param;
            }
        }
        if (bookingId != null && !bookingId.trim().isEmpty()) {
            MDC.put(MDC_BOOKING_KEY, bookingId);
            response.setHeader(BOOKING_ID_HEADER, bookingId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_BOOKING_KEY);
        }
    }
}
