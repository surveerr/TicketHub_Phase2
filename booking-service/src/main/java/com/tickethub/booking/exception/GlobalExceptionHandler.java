package com.tickethub.booking.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BookingNotFoundException.class) ResponseEntity<?> notFound(BookingNotFoundException e){return body(HttpStatus.NOT_FOUND,e.getMessage());}
    @ExceptionHandler(BookingBusinessException.class) ResponseEntity<?> business(BookingBusinessException e){return body(HttpStatus.CONFLICT,e.getMessage());}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){return body(HttpStatus.BAD_REQUEST,e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).collect(Collectors.joining(", ")));}
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<?> noResource(NoResourceFoundException e){return body(HttpStatus.NOT_FOUND,"No endpoint "+e.getResourcePath()+" (valid base path: /api/v1/bookings)");}
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) ResponseEntity<?> badMethod(HttpRequestMethodNotSupportedException e){return body(HttpStatus.METHOD_NOT_ALLOWED,e.getMessage());}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> unreadable(org.springframework.http.converter.HttpMessageNotReadableException e){return body(HttpStatus.BAD_REQUEST,"Malformed or missing request body");}
    @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){return body(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage()==null?"Unexpected error":e.getMessage());}
    private ResponseEntity<?> body(HttpStatus s,String m){return ResponseEntity.status(s).body(new ErrorResponse(Instant.now(),s.value(),s.getReasonPhrase(),m));}
    record ErrorResponse(Instant timestamp,int status,String error,String message){}
}
