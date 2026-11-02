// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.ports.exception.ExceptionCode;
import org.springframework.http.HttpStatus;

public enum ConnectServiceExceptionCodes implements ExceptionCode {
  DOES_NOT_EXIST("CONNECT-API-EXCEPTION-1000", "Does not exist", HttpStatus.NOT_FOUND),
  ALREADY_EXIST("CONNECT-API-EXCEPTION-1001", "Already exists", HttpStatus.CONFLICT),
  MISSING_INFORMATION(
      "CONNECT-API-EXCEPTION-1002", "Missing information in request", HttpStatus.BAD_REQUEST),
  FAILED_TO_CREATE(
      "CONNECT-API-EXCEPTION-1003",
      "Failed to create new source",
      HttpStatus.INTERNAL_SERVER_ERROR),
  FAILED_TO_DELETE(
      "CONNECT-API-EXCEPTION-1004", "Failed to delete source", HttpStatus.INTERNAL_SERVER_ERROR),
  SERVICE_UNAVAILABLE(
      "CONNECT-API-EXCEPTION-1005",
      "Connect service is unavailable",
      HttpStatus.SERVICE_UNAVAILABLE),
  UNEXPECTED_ERROR(
      "CONNECT-API-EXCEPTION-9999",
      "Unexpected error when calling connect-api",
      HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String description;
  private final HttpStatus httpStatus;

  ConnectServiceExceptionCodes(String code, String description, HttpStatus httpStatus) {
    this.code = code;
    this.description = description;
    this.httpStatus = httpStatus;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
