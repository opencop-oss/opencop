// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import com.opencop.core.sdk.technical.model.mapper.MultiPayloadMapper;
import com.opencop.core.sdk.technical.model.serializer.ProtobufSerializer;
import com.opencop.websocket.ports.business.usecase.event.UserEventHandler;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import java.util.List;
import java.util.UUID;
import opencop.core.proto.websocket.SocketProtos;
import tools.jackson.databind.ObjectMapper;

public class RetrieveAllEventsHandler implements UserEventHandler<String> {

  private final SocketProducer webSocketProducer;
  private final GeoEventReader reader;
  private final ObjectMapper objectMapper;
  private final ProtobufSerializer<List<byte[]>, SocketProtos.SocketMessage.MultiPayload>
      serializer;

  RetrieveAllEventsHandler(
      final GeoEventReader reader,
      final SocketProducer webSocketProducer,
      final ObjectMapper objectMapper) {
    this.reader = reader;
    this.webSocketProducer = webSocketProducer;
    this.objectMapper = objectMapper;
    this.serializer =
        new ProtobufSerializer<>(
            new MultiPayloadMapper(), SocketProtos.SocketMessage.MultiPayload.class);
  }

  @Override
  public void handle(UUID userId, String source, String destination) {
    int pageSize = 5000;
    long offset = 0;

    var command = new Command(source, State.STARTING);
    webSocketProducer.sendToUser(
        userId, "/user/queue/messages", objectMapper.writeValueAsBytes(command), false);

    while (true) {
      var page = reader.readPage(source, offset, pageSize);
      if (page.consumedMembers() == 0) {
        break;
      }

      var events = page.payloads();

      if (!events.isEmpty()) {
        webSocketProducer.sendToUser(
            userId, destination, serializer.serialize(events), events.size() > 1000);
      }

      offset += page.consumedMembers();
    }
    command = new Command(source, State.FINISHED);
    webSocketProducer.sendToUser(
        userId, "/user/queue/messages", objectMapper.writeValueAsBytes(command), false);
  }

  private enum State {
    STARTING,
    FINISHED,
  }

  private record Command(String source, State state) {}
}
