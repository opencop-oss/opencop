// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.ports.exception.ExceptionCode;
import org.springframework.http.HttpStatus;

public enum IamServiceExceptionCodes implements ExceptionCode {
  SERVICE_UNAVAILABLE(
      "IAM-API-EXCEPTION-1000", "IAM service is unavailable", HttpStatus.SERVICE_UNAVAILABLE),
  UNEXPECTED_ERROR(
      "IAM-API-EXCEPTION-9999",
      "Unexpected error when calling IAM service",
      HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String description;
  private final HttpStatus httpStatus;

  IamServiceExceptionCodes(String code, String description, HttpStatus httpStatus) {
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
