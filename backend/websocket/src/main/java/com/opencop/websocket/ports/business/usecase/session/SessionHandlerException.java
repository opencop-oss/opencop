// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.session;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.websocket.ports.business.usecase.event.EventHandlerExceptionCodes;
import org.springframework.http.HttpStatus;

public class SessionHandlerException extends GlobalOpenCopException {

  public SessionHandlerException(EventHandlerExceptionCodes exception, HttpStatus httpStatus) {
    super(exception.code(), exception.description(), httpStatus);
  }

  public SessionHandlerException(String code, String message, HttpStatus httpStatus) {
    super(message, code, httpStatus);
  }
}
