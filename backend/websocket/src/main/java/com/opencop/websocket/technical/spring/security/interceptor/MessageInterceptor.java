// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security.interceptor;

import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.springframework.web.socket.WebSocketSession;

public interface MessageInterceptor {
  public boolean preHandle(SocketMessage message, WebSocketSession session)
      throws MessageInterceptorException;

  public void postHandle(
      SocketMessage message, WebSocketSession session, boolean sent, Exception ex);
}
