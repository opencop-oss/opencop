// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import org.junit.jupiter.api.Test;

class WebSocketHandshakeComponentIT extends AbstractWebSocketComponentIT {

  @Test
  void registersAndRemovesSessionAcrossTheRealWebSocketHandshake() throws Exception {
    assertThat(pingDatabase()).isEqualTo(1);

    var clientHandler = new RecordingBinaryWebSocketHandler();
    var session = fixture.connect(websocketUrl(), clientHandler);

    var userSession = awaitSingleUserSession();
    assertThat(session.isOpen()).isTrue();
    assertThat(sessionRegistry.getUsersSessions()).hasSize(1);
    assertThat(sessionRegistry.getUsersSessions()).contains(userSession);
    assertThat(clientHandler.transportError()).isNull();

    session.close();

    var closeStatus = awaitCloseStatus(clientHandler, "client close status");
    assertThat(closeStatus.getCode()).isEqualTo(1000);
    awaitNoRegisteredSessions();
  }
}
