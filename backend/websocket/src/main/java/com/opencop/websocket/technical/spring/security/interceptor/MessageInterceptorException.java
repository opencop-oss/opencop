// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security.interceptor;

import org.springframework.web.socket.CloseStatus;

public class MessageInterceptorException extends Exception {
  private final CloseStatus status;

  public MessageInterceptorException(Throwable throwable, CloseStatus status) {
    super(throwable);
    this.status = status;
  }

  public MessageInterceptorException(CloseStatus status) {
    this.status = status;
  }

  public CloseStatus getStatus() {
    return status;
  }
}
