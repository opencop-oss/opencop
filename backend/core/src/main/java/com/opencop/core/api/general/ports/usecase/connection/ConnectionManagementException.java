// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection;

import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import com.opencop.core.sdk.ports.exception.ErrorDetail;
import com.opencop.core.sdk.ports.exception.ExceptionCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.api.ConnectServiceException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ConnectionManagementException extends GlobalOpenCopException {

  public ConnectionManagementException(String code, String message, HttpStatus httpStatus) {
    super(code, message, httpStatus);
  }

  public ConnectionManagementException(ExceptionCode exceptionCode) {
    super(exceptionCode.code(), exceptionCode.description(), exceptionCode.httpStatus());
  }

  public ConnectionManagementException(ExceptionCode exceptionCode, Object... formatArgs) {
    super(exceptionCode.code(), exceptionCode.format(formatArgs), exceptionCode.httpStatus());
  }

  public ConnectionManagementException(ConnectServiceException e) {
    super(e.getCode(), e.getMessage(), e.getHttpStatus());
  }

  private ConnectionManagementException(
      ExceptionCode exceptionCode, String detailCode, String field, Object... formatArgs) {
    super(
        exceptionCode.code(),
        exceptionCode.format(formatArgs),
        exceptionCode.httpStatus(),
        detailCode,
        field);
  }

  private ConnectionManagementException(
      ExceptionCode exceptionCode, String field, List<ErrorDetail> details) {
    super(
        exceptionCode.code(),
        exceptionCode.format(),
        exceptionCode.httpStatus(),
        null,
        field,
        details);
  }

  /**
   * The connection does not exist, or the caller cannot see it: both answer alike, so the answer
   * never confirms that a connection the caller may not see exists.
   */
  public static ConnectionManagementException connectionNotFound(UUID sourceId) {
    return new ConnectionManagementException(
        null, "Data source not found: " + sourceId, HttpStatus.NOT_FOUND);
  }

  /**
   * The integration settings broke one or more rules; each is attached as a detail targeting the
   * rejected location within the settings.
   */
  public static ConnectionManagementException settingsRejected(
      String field, List<SettingsViolation> violations) {
    return new ConnectionManagementException(
        ConnectionExceptionCodes.INVALID_CONNECTION_SETTINGS,
        field,
        violations.stream().map(SettingsViolation::toErrorDetail).toList());
  }

  /** A failure that concerns one request field, which a client can point at. */
  public static ConnectionManagementException forField(
      ExceptionCode exceptionCode, String field, Object... formatArgs) {
    return new ConnectionManagementException(exceptionCode, null, field, formatArgs);
  }

  /**
   * An extractor rejected the input it was given, with its own safe explanation, the finer reason,
   * and the declared field it concerns when it named one.
   */
  public static ConnectionManagementException inputRejected(ConnectionInputException rejection) {
    return new ConnectionManagementException(
        ConnectionExceptionCodes.CONNECTION_INPUT_INVALID,
        rejection.providerDetailCode().orElse(null),
        rejection.field().orElse(null),
        rejection.getMessage());
  }

  /** OpenCOP rejected input an extractor accepted, for the given finer reason. */
  public static ConnectionManagementException inputRejected(String detailCode, String message) {
    return new ConnectionManagementException(
        ConnectionExceptionCodes.CONNECTION_INPUT_INVALID, detailCode, null, message);
  }

  /** OpenCOP rejected input before any extractor saw it, for the given reason and field. */
  public static ConnectionManagementException inputRejected(
      String detailCode, String field, String message) {
    return new ConnectionManagementException(
        ConnectionExceptionCodes.CONNECTION_INPUT_INVALID, detailCode, field, message);
  }
}
