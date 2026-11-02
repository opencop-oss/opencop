// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.me;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class UserSpecificException extends GlobalOpenCopException {

  public UserSpecificException(String code, String description, HttpStatus httpStatus) {
    super(code, description, httpStatus);
  }

  public UserSpecificException(UserSpecificExceptionCodes exception, HttpStatus httpStatus) {
    super(exception.code(), exception.description(), httpStatus);
  }
}
