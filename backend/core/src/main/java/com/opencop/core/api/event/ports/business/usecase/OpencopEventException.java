// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class OpencopEventException extends GlobalOpenCopException {

  public OpencopEventException(EventExceptionCodes error, HttpStatus status) {
    super(error.code(), error.description(), status);
  }
}
