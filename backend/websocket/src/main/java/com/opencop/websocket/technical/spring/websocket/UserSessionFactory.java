// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import com.opencop.websocket.technical.spring.config.WebSocketProtocolProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public final class UserSessionFactory {

  private final WebSocketProtocolProperties properties;

  public UserSessionFactory(WebSocketProtocolProperties properties) {
    this.properties = properties;
  }

  public UserSession create(WebSocketSession webSocketSession) {
    return new UserSession(
        webSocketSession,
        new ProtocolViolationTracker(
            properties.getProtocolViolationLimit(), properties.getProtocolViolationWindow()));
  }
}
