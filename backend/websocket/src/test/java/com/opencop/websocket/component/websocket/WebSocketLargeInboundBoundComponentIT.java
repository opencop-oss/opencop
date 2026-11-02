// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.WebSocketComponentTestApplication;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@SpringBootTest(
    classes = WebSocketComponentTestApplication.class,
    properties = {"websocket.max-inbound-message-size=128KB"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketLargeInboundBoundComponentIT extends AbstractWebSocketComponentIT {

  private static final int MAX_INBOUND_MESSAGE_BYTES = 131_072;
  private static final int ABOVE_CONTAINER_FRAME_DEFAULT_BYTES = 100_000;

  @Test
  void configuredLimitAboveTheContainerFrameDefaultGovernsUnfragmentedBinaryMessages()
      throws Exception {
    var handler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), handler);
    awaitSingleUserSession();

    fixture.send(session, new byte[ABOVE_CONTAINER_FRAME_DEFAULT_BYTES]);

    var close = awaitCloseStatus(handler, "unfragmented binary message above the frame default");
    assertThat(close.getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
  }

  @Test
  void configuredLimitAboveTheContainerFrameDefaultStillRejectsOversizedBinaryMessages()
      throws Exception {
    var oversizedHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession oversizedSession = fixture.connect(websocketUrl(), oversizedHandler);
    var healthyHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession healthySession = fixture.connect(websocketUrl(), healthyHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "two sessions before the oversized binary message");

    fixture.send(oversizedSession, new byte[MAX_INBOUND_MESSAGE_BYTES + 1]);

    var oversizedClose = awaitCloseStatus(oversizedHandler, "oversized binary close above 64KB");
    assertThat(oversizedClose.getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    assertThat(oversizedHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "oversized binary socket cleanup");
    assertThat(healthySession.isOpen()).isTrue();
    assertThat(healthyHandler.closeStatus()).isNull();
  }
}
