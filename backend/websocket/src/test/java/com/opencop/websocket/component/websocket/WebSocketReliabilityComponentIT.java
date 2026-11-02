// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import com.opencop.websocket.technical.spring.websocket.OCMPWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class WebSocketReliabilityComponentIT extends AbstractWebSocketComponentIT {

  @Autowired private OCMPWebSocketHandler webSocketHandler;

  @Test
  void storesConnectedSessionsAsConcurrentDecorators() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession clientSession = fixture.connect(websocketUrl(), clientHandler);

    var userSession = awaitSingleUserSession();

    assertThat(clientSession.isOpen()).isTrue();
    assertThat(userSession.getWebsocketSession())
        .isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
  }

  @Test
  void transportErrorsRemoveTheSessionFromTheRegistry() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    webSocketHandler.handleTransportError(
        userSession.getWebsocketSession(), new IllegalStateException("component-test transport"));

    awaitNoRegisteredSessions();
    var closeStatus = awaitCloseStatus(clientHandler, "transport error close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.SERVER_ERROR.getCode());
  }

  @Test
  void heartbeatAfterRegistryRemovalClosesTheSessionInsteadOfThrowingInsideTheHandler()
      throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession clientSession = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    sessionExpiryKiller.cancel(userSession);
    sessionRegistry.remove(userSession);

    fixture.send(clientSession, fixture.heartbeat());

    var closeStatus = awaitCloseStatus(clientHandler, "unregistered heartbeat close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.SERVER_ERROR.getCode());
    awaitNoRegisteredSessions();
  }
}
