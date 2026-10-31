package com.tickethub.eventshow.exception;

import com.tickethub.eventshow.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private ErrorResponse e(int status, String code, String message, HttpServletRequest r) {
        return new ErrorResponse(Instant.now(), status, code, message, r.getRequestURI());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException x, HttpServletRequest r) {
        return ResponseEntity.status(404).body(e(404, "RESOURCE_NOT_FOUND", x.getMessage(), r));
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorResponse> conflict(Exception x, HttpServletRequest r) {
        String m = x instanceof ConflictException ? x.getMessage() : "Resource already exists or is still in use";
        String code = m != null && m.contains("HOLD_EXPIRED") ? "HOLD_EXPIRED" : m != null && m.contains("SHOW_OVERLAP") ? "SHOW_OVERLAP" : m != null && m.contains("SEAT_NOT_AVAILABLE") ? "SEAT_NOT_AVAILABLE" : m != null && m.contains("INVALID_STATE_TRANSITION") ? "INVALID_STATE_TRANSITION" : "RESOURCE_CONFLICT";
        return ResponseEntity.status(409).body(e(409, code, m, r));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ErrorResponse> business(BusinessRuleException x, HttpServletRequest r) {
        String code = x.getMessage() != null && x.getMessage().contains("SHOW_NOT_OPEN") ? "SHOW_NOT_OPEN" : "BUSINESS_RULE_VIOLATION";
        return ResponseEntity.status(422).body(e(422, code, x.getMessage(), r));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException x, HttpServletRequest r) {
        String m = x.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(e(400, "VALIDATION_FAILED", m, r));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> bad(IllegalArgumentException x, HttpServletRequest r) {
        return ResponseEntity.badRequest().body(e(400, "VALIDATION_FAILED", x.getMessage(), r));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException x, HttpServletRequest r) {
        int status = x.getStatusCode().value();
        String code = status == 401 ? "UNAUTHORIZED" : status == 403 ? "FORBIDDEN" : "REQUEST_ERROR";
        return ResponseEntity.status(status).body(e(status, code, x.getReason(), r));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException x, HttpServletRequest r) {
        return ResponseEntity.badRequest().body(e(400, "INVALID_REQUEST_BODY", "Request body is missing or invalid JSON", r));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> generic(Exception x, HttpServletRequest r) {
        return ResponseEntity.status(500).body(e(500, "INTERNAL_ERROR", "Unexpected error", r));
    }
}
