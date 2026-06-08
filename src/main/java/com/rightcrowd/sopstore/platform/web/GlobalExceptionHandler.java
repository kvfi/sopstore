package com.rightcrowd.sopstore.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Handles exceptions globally across the JSON API and maps them to error payloads. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Represents an API error payload with status, code, and message. */
  public record ApiError(int status, String code, String message) {}

  /** Handles illegal argument exceptions by returning a 400 bad request response. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
    String message = ex.getMessage();
    return ResponseEntity.badRequest()
        .body(new ApiError(400, "bad_request", message != null ? message : "Bad request"));
  }

  /** Handles security exceptions by returning a 403 forbidden JSON payload. */
  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ApiError> forbidden(SecurityException ex) {
    String message = ex.getMessage();
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiError(403, "forbidden", message != null ? message : "Forbidden"));
  }
}
