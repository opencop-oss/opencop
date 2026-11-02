// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.usecase.event;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class EventHandlerException extends GlobalOpenCopException {

  public EventHandlerException(String code, String message, HttpStatus httpStatus) {
    super(message, code, httpStatus);
  }
}
