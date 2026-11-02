// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.exception;

import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.sql.SQLException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionManager {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionManager.class);

  @ExceptionHandler(GlobalOpenCopException.class)
  public ResponseEntity<GlobalErrorResponse> handleGlobalOpenCopException(
      GlobalOpenCopException ex, WebRequest request) {
    // Logging here would over-report errors because we log in the GlobalOpenCopException
    // constructor
    return ResponseEntity.status(ex.getHttpStatus())
        .body(
            new GlobalErrorResponse(
                ex.getCode(), ex.getMessage(), ex.getDetailCode(), ex.getField(), ex.getDetails()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<GlobalErrorResponse> handleJsonParseError(
      HttpMessageNotReadableException ex, WebRequest request) {

    // Exception details may be sensitive if they include payload info
    if (log.isDebugEnabled()) {
      log.warn("JSON parse error", ex);
    } else {
      log.warn("JSON parse error");
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new GlobalErrorResponse("INVALID_REQUEST", "Invalid request body"));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<GlobalErrorResponse> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex, WebRequest request) {
    log.warn("Unsupported content type: {}", ex.getContentType());
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(
            new GlobalErrorResponse(
                "UNSUPPORTED_MEDIA_TYPE", "The request's content type is not supported"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<GlobalErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, WebRequest request) {
    log.warn("Unsupported method: {}", ex.getMethod());
    var supported = ex.getSupportedHttpMethods();
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .allow(supported == null ? new HttpMethod[0] : supported.toArray(HttpMethod[]::new))
        .body(
            new GlobalErrorResponse(
                "METHOD_NOT_ALLOWED", "The request's method is not supported by this route"));
  }

  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<GlobalErrorResponse> handleUnsupportedOperation(
      UnsupportedOperationException ex, WebRequest request) {
    log.warn("Unsupported operation: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new GlobalErrorResponse(
                "UNSUPPORTED_OPERATION",
                ex.getMessage() != null ? ex.getMessage() : "Operation not supported"));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<GlobalErrorResponse> handleMissingParam(
      MissingServletRequestParameterException ex, WebRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new GlobalErrorResponse(
                "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing"));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<GlobalErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, WebRequest request) {
    log.warn("Invalid parameter '{}': {}", ex.getName(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new GlobalErrorResponse(
                "INVALID_PARAMETER", "Invalid value for parameter '" + ex.getName() + "'"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<GlobalErrorResponse> handleResponseStatusException(
      ResponseStatusException ex, WebRequest request) {
    log.warn("ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
    return ResponseEntity.status(ex.getStatusCode())
        .body(new GlobalErrorResponse("HTTP_ERROR", ex.getReason()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<GlobalErrorResponse> handleNoResourceFound(
      NoResourceFoundException ex, WebRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new GlobalErrorResponse("NOT_FOUND", "Resource not found"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<GlobalErrorResponse> handleInternalServer(
      Exception ex, WebRequest request) {
    var lifecycleConflict = lifecycleConflictMessage(ex);
    if (lifecycleConflict != null) {
      log.warn("Lifecycle conflict rejected by database guard: {}", ex.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new GlobalErrorResponse("LIFECYCLE_CONFLICT", lifecycleConflict));
    }
    log.error("Unhandled exception: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new GlobalErrorResponse("INTERNAL_ERROR", "Something went wrong."));
  }

  // V37's deferred watchbox/layer lifecycle triggers reject at commit time, so their failures
  // arrive wrapped in whichever transaction or persistence exception the current Spring/JPA
  // versions produce. Matching the migration-owned custom SQLSTATEs from the cause chain keeps
  // this translation stable across those wrappers. The messages are deliberately generic: they
  // must not enumerate or name the referencing watchboxes (see V37__watchbox_detection.sql).
  private static final Map<String, String> LIFECYCLE_CONFLICT_SQL_STATES =
      Map.of(
          "23U01", "A selected layer is not active.",
          "23U02", "The layer is referenced by active watchboxes.");

  private static String lifecycleConflictMessage(Throwable ex) {
    var depth = 0;
    for (var cause = ex; cause != null && depth < 20; cause = cause.getCause(), depth++) {
      // A connection failure, such as a lost database, carries no SQL state.
      if (cause instanceof SQLException sql && sql.getSQLState() != null) {
        var message = LIFECYCLE_CONFLICT_SQL_STATES.get(sql.getSQLState());
        if (message != null) {
          return message;
        }
      }
    }
    return null;
  }
}
