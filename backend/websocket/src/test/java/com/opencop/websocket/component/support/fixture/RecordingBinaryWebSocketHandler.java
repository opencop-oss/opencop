// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.support.fixture;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

public class RecordingBinaryWebSocketHandler extends AbstractWebSocketHandler {

  private final BlockingQueue<SocketMessage> messages = new LinkedBlockingQueue<>();
  private volatile CloseStatus closeStatus;
  private volatile Throwable transportError;

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message)
      throws Exception {
    messages.add(SocketMessage.parseFrom(message.getPayload()));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    throw new AssertionError("Unexpected text frame: " + message.getPayload());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    this.transportError = exception;
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    this.closeStatus = status;
  }

  public SocketMessage pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
    return messages.poll(timeout, unit);
  }

  public int messageCount() {
    return messages.size();
  }

  public void clearMessages() {
    messages.clear();
  }

  public CloseStatus closeStatus() {
    return closeStatus;
  }

  public Throwable transportError() {
    return transportError;
  }
}
