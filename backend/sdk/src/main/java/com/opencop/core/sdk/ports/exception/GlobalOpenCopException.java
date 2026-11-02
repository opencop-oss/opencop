// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.exception;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Handled by GlobalExceptionManager.handleGlobalOpenCopException. Policy to avoid duplicate error
 * logging: global handler does not log, log in GlobalOpenCopException constructors.
 */
public class GlobalOpenCopException extends RuntimeException {

  private static final Logger log = LoggerFactory.getLogger(GlobalOpenCopException.class);
  private final String code;
  private final HttpStatus httpStatus;
  private final String detailCode;
  private final String field;
  private final List<ErrorDetail> details;

  public GlobalOpenCopException(String message, HttpStatus httpStatus) {
    super(message);
    this.code = null;
    this.httpStatus = httpStatus;
    this.detailCode = null;
    this.field = null;
    this.details = List.of();
    logForStatus("{} | returning {}", message, httpStatus.getReasonPhrase());
  }

  public GlobalOpenCopException(String code, String message, HttpStatus httpStatus) {
    this(code, message, httpStatus, null, null, List.of());
  }

  /**
   * An error enriched with more specific facts, which the response lists under {@code details}. Any
   * route may enrich its error this way instead of adding a response field.
   */
  public GlobalOpenCopException(
      String code, String message, HttpStatus httpStatus, List<ErrorDetail> details) {
    this(code, message, httpStatus, null, null, details);
  }

  /**
   * @param detailCode finer-grained reason within the code, or null
   * @param field the request field the failure concerns, or null
   */
  protected GlobalOpenCopException(
      String code, String message, HttpStatus httpStatus, String detailCode, String field) {
    this(code, message, httpStatus, detailCode, field, List.of());
  }

  /**
   * @param detailCode finer-grained reason within the code, or null
   * @param field the request field the failure concerns, or null
   * @param details more specific facts the response lists under {@code details}, in order
   */
  protected GlobalOpenCopException(
      String code,
      String message,
      HttpStatus httpStatus,
      String detailCode,
      String field,
      List<ErrorDetail> details) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
    this.detailCode = detailCode;
    this.field = field;
    this.details = List.copyOf(Objects.requireNonNull(details, "details"));
    logForStatus("{}: {} | returning {}", code, message, httpStatus.getReasonPhrase());
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  /** Returns the finer-grained reason within the code, or null when the failure has none. */
  public String getDetailCode() {
    return detailCode;
  }

  /** Returns the request field the failure concerns, or null when it concerns none. */
  public String getField() {
    return field;
  }

  /** Returns the details this error carries, in order. */
  public List<ErrorDetail> getDetails() {
    return details;
  }

  private void logForStatus(String formatStr, Object... args) {
    if (httpStatus != null && httpStatus.is5xxServerError()) {
      log.error(formatStr, args);
    } else {
      log.warn(formatStr, args);
    }
  }
}
