// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when IAM service calls fail. Extends GlobalOpenCopException so it's
 * automatically handled by GlobalExceptionManager.
 */
public class IamServiceException extends GlobalOpenCopException {

  public IamServiceException(IamServiceExceptionCodes exception) {
    super(exception.code(), exception.description(), exception.httpStatus());
  }

  public IamServiceException(String code, String message, HttpStatus httpStatus) {
    super(code, message, httpStatus);
  }
}
