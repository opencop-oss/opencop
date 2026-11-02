// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.websocket;

import com.opencop.websocket.ports.business.usecase.event.UserEventHandler;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class WebSocketEventListener {
  private final UserEventHandler<String> websocketEventHandler;

  public WebSocketEventListener(
      @Qualifier("retrieveAllEventsHandler") UserEventHandler<String> eventHandler) {
    this.websocketEventHandler = eventHandler;
  }

  public void onSubscribe(String destination, UUID userId) {
    if (destination.startsWith("/topic/queue/event/")) {
      String sourceKey = destination.replace("/topic/queue/event/", "");
      // Send events back to same destination.
      this.websocketEventHandler.handle(userId, sourceKey, destination);
    }
  }
}
