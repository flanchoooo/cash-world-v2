package com.poscloud.wallet.common;

import java.time.Instant;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ErrorHandler {
  @io.swagger.v3.oas.annotations.media.Schema(name = "ErrorHandlerError")
  public record Error(String code, String message, Instant timestamp) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Error> domain(ApiException e) {
    var status =
        e.getCode().endsWith("NOT_FOUND")
            ? HttpStatus.NOT_FOUND
            : e.getCode().equals("FORBIDDEN")
                ? HttpStatus.FORBIDDEN
                : e.getCode().equals("UNAUTHORIZED")
                    ? HttpStatus.UNAUTHORIZED
                    : e.getCode().contains("ALREADY") || e.getCode().contains("IDEMPOTENCY")
                        ? HttpStatus.CONFLICT
                        : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(new Error(e.getCode(), e.getMessage(), Instant.now()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<Error> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(new Error("INVALID_REQUEST", "Request validation failed", Instant.now()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Error> denied(Exception e) {
    return ResponseEntity.status(403).body(new Error("FORBIDDEN", "Access denied", Instant.now()));
  }

  @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
  ResponseEntity<Error> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(
            new Error(
                "CONFLICT",
                "Conflicting request; retry with the same idempotency key",
                Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Error> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(getClass()).error("Unhandled request failure", e);
    return ResponseEntity.internalServerError()
        .body(new Error("INTERNAL_ERROR", "Request could not be completed", Instant.now()));
  }
}
