// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security.interceptor;

import com.opencop.websocket.technical.spring.security.AuthConstants;
import org.springframework.web.socket.CloseStatus;

public class AuthFailException extends MessageInterceptorException {
  public AuthFailException(Throwable throwable) {
    super(throwable, new CloseStatus(AuthConstants.AUTH_FAIL));
  }

  public AuthFailException() {
    super(new CloseStatus(AuthConstants.AUTH_FAIL));
  }
}
