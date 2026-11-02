// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public final class InboundMessageLimitWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

  private final int maxInboundMessageBytes;

  public InboundMessageLimitWebSocketHandlerDecorator(
      WebSocketHandler delegate, int maxInboundMessageBytes) {
    super(delegate);
    this.maxInboundMessageBytes = maxInboundMessageBytes;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    session.setTextMessageSizeLimit(maxInboundMessageBytes);
    session.setBinaryMessageSizeLimit(maxInboundMessageBytes);
    super.afterConnectionEstablished(session);
  }
}
