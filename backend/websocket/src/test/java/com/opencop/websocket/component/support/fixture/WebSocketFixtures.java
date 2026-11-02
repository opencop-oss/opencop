// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.support.fixture;

import jakarta.websocket.ContainerProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

public class WebSocketFixtures {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final long CONNECT_TIMEOUT_SECONDS = 5;
  private static final int CLIENT_INBOUND_MESSAGE_LIMIT = 2 * 1_024 * 1_024;

  private final StandardWebSocketClient client = clientWithLargeInboundBuffer();
  private final List<WebSocketSession> sessions = new ArrayList<>();

  public void reset() {
    for (WebSocketSession session : List.copyOf(sessions)) {
      if (session.isOpen()) {
        try {
          session.close();
        } catch (IOException ignored) {
        }
      }
    }
    sessions.clear();
  }

  public WebSocketSession connect(String url, WebSocketHandler handler) throws Exception {
    return connect(url, handler, new WebSocketHttpHeaders());
  }

  public WebSocketSession connect(
      String url, WebSocketHandler handler, WebSocketHttpHeaders headers) throws Exception {
    WebSocketSession session =
        client
            .execute(handler, headers, URI.create(url))
            .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    sessions.add(session);
    return session;
  }

  public void send(WebSocketSession session, SocketMessage message) throws IOException {
    session.sendMessage(new BinaryMessage(message.toByteArray()));
  }

  public void send(WebSocketSession session, byte[] payload) throws IOException {
    session.sendMessage(new BinaryMessage(payload));
  }

  public void send(WebSocketSession session, byte[] payload, boolean last) throws IOException {
    session.sendMessage(new BinaryMessage(payload, last));
  }

  public void sendText(WebSocketSession session, String payload) throws IOException {
    session.sendMessage(new TextMessage(payload));
  }

  public void sendText(WebSocketSession session, String payload, boolean last) throws IOException {
    session.sendMessage(new TextMessage(payload, last));
  }

  public void sendPong(WebSocketSession session, byte[] payload) throws IOException {
    session.sendMessage(new PongMessage(ByteBuffer.wrap(payload)));
  }

  public SocketMessage subscribe(UUID sourceId) {
    return SocketMessage.newBuilder()
        .setType(SocketMessage.Type.SUBSCRIBE)
        .setDestination("/topic/queue/event/" + sourceId)
        .build();
  }

  public SocketMessage unsubscribe(UUID sourceId) {
    return SocketMessage.newBuilder()
        .setType(SocketMessage.Type.UNSUBSCRIBE)
        .setDestination("/topic/queue/event/" + sourceId)
        .build();
  }

  public SocketMessage heartbeat() {
    return SocketMessage.newBuilder().setType(SocketMessage.Type.HEARTBEAT).build();
  }

  public SocketMessage subscribe(String destination) {
    return SocketMessage.newBuilder()
        .setType(SocketMessage.Type.SUBSCRIBE)
        .setDestination(destination)
        .build();
  }

  public UUID sameCharUuid(char hexDigit) {
    String value = String.valueOf(Character.toLowerCase(hexDigit));
    if ("0123456789abcdef".indexOf(value.charAt(0)) < 0) {
      throw new IllegalArgumentException("hexDigit must be a hexadecimal character");
    }
    return UUID.fromString(
        value.repeat(8)
            + "-"
            + value.repeat(4)
            + "-"
            + value.repeat(4)
            + "-"
            + value.repeat(4)
            + "-"
            + value.repeat(12));
  }

  public Duration defaultTimeout() {
    return DEFAULT_TIMEOUT;
  }

  private static StandardWebSocketClient clientWithLargeInboundBuffer() {
    var container = ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxTextMessageBufferSize(CLIENT_INBOUND_MESSAGE_LIMIT);
    container.setDefaultMaxBinaryMessageBufferSize(CLIENT_INBOUND_MESSAGE_LIMIT);
    return new StandardWebSocketClient(container);
  }
}
