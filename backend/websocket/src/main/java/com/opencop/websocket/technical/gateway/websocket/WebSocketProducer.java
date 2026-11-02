// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.websocket;

import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.websocket.OCMPMessagingTemplate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WebSocketProducer implements SocketProducer {

  private final OCMPMessagingTemplate template;

  public WebSocketProducer(final OCMPMessagingTemplate template) {
    this.template = template;
  }

  @Override
  public void sendToUser(UUID userId, String destination, byte[] event, boolean compress) {
    template.sendToUser(userId, destination, event, compress);
  }

  @Override
  public boolean sendToUser(
      UUID userId,
      String destination,
      byte[] event,
      Map<String, String> headers,
      boolean compress) {
    return template.sendToUser(userId, destination, event, headers, compress);
  }

  @Override
  public void broadcast(String topic, byte[] event, boolean compress) {
    template.broadcast(topic, event, compress);
  }

  @Override
  public CompletableFuture<SendAttemptResult> sendToUserAwait(
      UUID userId,
      String destination,
      byte[] event,
      Map<String, String> headers,
      boolean compress) {
    return template.sendToUserAwait(userId, destination, event, headers, compress);
  }
}
