// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_BACKING_SESSION_ID;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.WebSocketSession;

public class UserSession {
  static final int SEND_TIME_LIMIT_MS = 10_000;
  static final int BUFFER_SIZE_LIMIT = 1024 * 1024;

  private final UUID userId;
  private final WebSocketSession session;
  private final Set<String> destinations;
  private final String backingSessionId;
  private final Instant authenticationDeadline;
  private final boolean sessionless;
  private final ProtocolViolationTracker protocolViolationTracker;
  private boolean closing;

  public UserSession(
      WebSocketSession wsSession, ProtocolViolationTracker protocolViolationTracker) {
    this(wsSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT, protocolViolationTracker);
  }

  UserSession(
      WebSocketSession wsSession,
      int sendTimeLimitMs,
      int bufferSizeLimit,
      ProtocolViolationTracker protocolViolationTracker) {
    this.session = new AckingWebSocketSessionDecorator(wsSession, sendTimeLimitMs, bufferSizeLimit);
    this.destinations = ConcurrentHashMap.newKeySet();
    this.userId = UserSessionUtils.getUserIdFromSession(wsSession);
    this.authenticationDeadline =
        requireAttribute(wsSession, ATTR_EXP, Instant.class, "authentication deadline");
    this.sessionless = Boolean.TRUE.equals(wsSession.getAttributes().get(ATTR_SESSIONLESS));
    this.backingSessionId =
        sessionless
            ? null
            : requireAttribute(
                wsSession, ATTR_BACKING_SESSION_ID, String.class, "backing session id");
    this.protocolViolationTracker =
        Objects.requireNonNull(protocolViolationTracker, "protocolViolationTracker");
  }

  public UUID getUserId() {
    return userId;
  }

  public synchronized boolean addDestination(String destination) {
    if (closing) {
      return false;
    }
    return destinations.add(destination);
  }

  public void removeDestination(String destination) {
    destinations.remove(destination);
  }

  public WebSocketSession getWebsocketSession() {
    return session;
  }

  public String getBackingSessionId() {
    return backingSessionId;
  }

  public Instant getAuthenticationDeadline() {
    return authenticationDeadline;
  }

  public boolean isSessionless() {
    return sessionless;
  }

  ProtocolViolationTracker getProtocolViolationTracker() {
    return protocolViolationTracker;
  }

  synchronized void clearProtocolState() {
    destinations.clear();
    protocolViolationTracker.terminate();
  }

  synchronized boolean acquireSubscriptionCallbackPermit() {
    return !closing;
  }

  synchronized boolean beginClosing() {
    if (closing) {
      return false;
    }
    closing = true;
    return true;
  }

  synchronized boolean isClosing() {
    return closing;
  }

  public boolean isSubscribed(String destination) {
    return destinations.contains(destination);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserSession that)) {
      return false;
    }
    return session.getId().equals(that.session.getId());
  }

  @Override
  public int hashCode() {
    return session.getId().hashCode();
  }

  private static <T> T requireAttribute(
      WebSocketSession session, String name, Class<T> type, String description) {
    Object value = session.getAttributes().get(name);
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("missing or invalid " + description);
    }
    return Objects.requireNonNull(type.cast(value));
  }
}
