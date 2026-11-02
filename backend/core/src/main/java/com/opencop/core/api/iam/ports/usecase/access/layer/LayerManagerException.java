// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.layer;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class LayerManagerException extends GlobalOpenCopException {

  public LayerManagerException(String code, String description, HttpStatus httpStatus) {
    super(code, description, httpStatus);
  }

  public LayerManagerException(LayerExceptionCodes exception, HttpStatus httpStatus) {
    super(exception.code(), exception.description(), httpStatus);
  }
}
