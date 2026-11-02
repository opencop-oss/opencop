// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.runtimefeature;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class RuntimeFeatureException extends GlobalOpenCopException {

  public RuntimeFeatureException(
      RuntimeFeatureErrorCode code, String message, HttpStatus httpStatus) {
    super(code.name(), message, httpStatus);
  }
}
