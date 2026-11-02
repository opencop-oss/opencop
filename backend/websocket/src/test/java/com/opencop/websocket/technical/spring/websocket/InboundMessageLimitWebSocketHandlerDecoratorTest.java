// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class InboundMessageLimitWebSocketHandlerDecoratorTest {

  @Test
  void appliesBothInboundLimitsBeforePublishingTheEstablishedSession() throws Exception {
    WebSocketHandler delegate = mock(WebSocketHandler.class);
    WebSocketSession session = mock(WebSocketSession.class);
    var decorator = new InboundMessageLimitWebSocketHandlerDecorator(delegate, 2_048);

    decorator.afterConnectionEstablished(session);

    var ordered = inOrder(session, delegate);
    ordered.verify(session).setTextMessageSizeLimit(2_048);
    ordered.verify(session).setBinaryMessageSizeLimit(2_048);
    ordered.verify(delegate).afterConnectionEstablished(session);
  }
}
