package com.personalfinance.config;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
    return error(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return error(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraintViolation(
      ConstraintViolationException ex) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleUnreadableBody(
      HttpMessageNotReadableException ex) {
    return error(HttpStatus.BAD_REQUEST, "Malformed or missing request body");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataIntegrity(
      DataIntegrityViolationException ex) {
    return error(
        HttpStatus.CONFLICT,
        "The operation conflicts with existing data (referential integrity constraint).");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    return error(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
    return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "error", status.getReasonPhrase(),
                "message", message != null ? message : "",
                "status", status.value(),
                "timestamp", LocalDateTime.now().toString()));
  }
}
