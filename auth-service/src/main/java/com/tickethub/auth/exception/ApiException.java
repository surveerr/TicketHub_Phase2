package com.tickethub.auth.exception;

import org.springframework.http.HttpStatus;

/** Business error that maps to an HTTP status and a stable machine-readable code. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException badRequest(String code, String msg)   { return new ApiException(HttpStatus.BAD_REQUEST, code, msg); }
    public static ApiException unauthorized(String code, String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, code, msg); }
    public static ApiException forbidden(String code, String msg)    { return new ApiException(HttpStatus.FORBIDDEN, code, msg); }
    public static ApiException notFound(String code, String msg)     { return new ApiException(HttpStatus.NOT_FOUND, code, msg); }
    public static ApiException conflict(String code, String msg)     { return new ApiException(HttpStatus.CONFLICT, code, msg); }
    public static ApiException locked(String code, String msg)       { return new ApiException(HttpStatus.LOCKED, code, msg); }
}
