// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.connector;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class SocketConnectorException extends GlobalOpenCopException {

  public SocketConnectorException(SocketConnectorExceptionCodes exception, HttpStatus httpStatus) {
    super(exception.code(), exception.description(), httpStatus);
  }
}
