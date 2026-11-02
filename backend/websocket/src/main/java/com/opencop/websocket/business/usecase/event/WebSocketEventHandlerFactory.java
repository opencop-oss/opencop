// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import com.opencop.websocket.ports.business.usecase.event.EventHandler;
import com.opencop.websocket.ports.business.usecase.event.UserEventHandler;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

public class WebSocketEventHandlerFactory {

  private WebSocketEventHandlerFactory() {}

  public static EventHandler<List<String>> createOpenCopEventHandler(
      WsSessionRegistry sessionRegistry, GeoEventReader reader, SocketProducer webSocketProducer) {
    return new GeoEventHandler(sessionRegistry, reader, webSocketProducer);
  }

  public static UserEventHandler<String> onConnectEventHandler(
      final GeoEventReader reader,
      final SocketProducer webSocketProducer,
      final ObjectMapper objectMapper) {
    return new RetrieveAllEventsHandler(reader, webSocketProducer, objectMapper);
  }
}
