// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import org.springframework.http.HttpStatus;

public class ConnectServiceException extends Exception {

  private final String code;
  private final HttpStatus httpStatus;

  public ConnectServiceException(ConnectServiceExceptionCodes exception) {
    super(exception.description());
    this.code = exception.code();
    this.httpStatus = exception.httpStatus();
  }

  public ConnectServiceException(String code, String message, HttpStatus httpStatus) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
