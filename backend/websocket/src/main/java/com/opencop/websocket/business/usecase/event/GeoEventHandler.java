// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import com.opencop.core.sdk.technical.model.mapper.MultiPayloadMapper;
import com.opencop.core.sdk.technical.model.serializer.ProtobufSerializer;
import com.opencop.websocket.ports.business.usecase.event.EventHandler;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import java.util.List;
import opencop.core.proto.websocket.SocketProtos;

public class GeoEventHandler implements EventHandler<List<String>> {

  private final WsSessionRegistry sessionRegistry;
  private final GeoEventReader reader;
  private final SocketProducer webSocketProducer;
  private final ProtobufSerializer<List<byte[]>, SocketProtos.SocketMessage.MultiPayload>
      serializer;

  GeoEventHandler(
      WsSessionRegistry sessionRegistry, GeoEventReader reader, SocketProducer webSocketProducer) {
    this.sessionRegistry = sessionRegistry;
    this.reader = reader;
    this.webSocketProducer = webSocketProducer;
    this.serializer =
        new ProtobufSerializer<>(
            new MultiPayloadMapper(), SocketProtos.SocketMessage.MultiPayload.class);
  }

  @Override
  public void handle(String source, List<String> cacheKeys, String destination) {
    if (sessionRegistry.getDestinationSessions(destination).isEmpty()) return;
    if (cacheKeys == null || cacheKeys.isEmpty()) return;

    var events = reader.readObjects(source, cacheKeys);

    if (!events.isEmpty()) {
      webSocketProducer.broadcast(
          destination.formatted(source.replace(":", "/")), serializer.serialize(events), false);
    }
  }
}
