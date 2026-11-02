// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

class OCMPMessagingTemplateTest {

  @Test
  void broadcast_sendLimitOnOneSessionStillDeliversToHealthySessions() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UserSession overloadedSession = mock(UserSession.class);
    WebSocketSession overloadedWebSocket = mock(WebSocketSession.class);
    when(overloadedSession.getWebsocketSession()).thenReturn(overloadedWebSocket);
    when(overloadedSession.getUserId()).thenReturn(java.util.UUID.randomUUID());
    when(overloadedWebSocket.getId()).thenReturn("ws-overloaded");
    when(overloadedWebSocket.isOpen()).thenReturn(true);
    when(overloadedSession.isSubscribed("/topic/test")).thenReturn(true);
    org.mockito.Mockito.doThrow(
            new SessionLimitExceededException("overflow", CloseStatus.SESSION_NOT_RELIABLE))
        .when(overloadedWebSocket)
        .sendMessage(any(BinaryMessage.class));

    UserSession healthySession = mock(UserSession.class);
    WebSocketSession healthyWebSocket = mock(WebSocketSession.class);
    when(healthySession.getWebsocketSession()).thenReturn(healthyWebSocket);
    when(healthySession.getUserId()).thenReturn(java.util.UUID.randomUUID());
    when(healthyWebSocket.getId()).thenReturn("ws-healthy");
    when(healthyWebSocket.isOpen()).thenReturn(true);
    when(healthySession.isSubscribed("/topic/test")).thenReturn(true);

    LinkedHashSet<UserSession> sessions = new LinkedHashSet<>();
    sessions.add(overloadedSession);
    sessions.add(healthySession);
    when(registry.getDestinationSessions("/topic/test")).thenReturn(sessions);

    template.broadcast("/topic/test", new byte[] {0x01}, false);

    verify(sessionExpiryKiller)
        .closeAndCleanup(overloadedSession, CloseStatus.SESSION_NOT_RELIABLE);
    verify(healthyWebSocket).sendMessage(any(BinaryMessage.class));
    verify(healthyWebSocket, never()).close(any(CloseStatus.class));
    verify(registry, never()).remove(healthySession);
    verify(sessionExpiryKiller, never())
        .closeAndCleanup(healthySession, CloseStatus.SESSION_NOT_RELIABLE);
  }

  @Test
  void sendLimitFailure_closesSessionWithLimitStatus() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(userSession.getUserId()).thenReturn(java.util.UUID.randomUUID());
    when(webSocketSession.getId()).thenReturn("ws-send-limit");
    when(webSocketSession.isOpen()).thenReturn(true);
    when(userSession.isSubscribed("/topic/test")).thenReturn(true);
    SessionLimitExceededException failure =
        new SessionLimitExceededException("overflow", CloseStatus.SESSION_NOT_RELIABLE);
    org.mockito.Mockito.doThrow(failure)
        .when(webSocketSession)
        .sendMessage(any(BinaryMessage.class));

    boolean delivered =
        template.sendToUserSession(userSession, "/topic/test", new byte[] {0x01}, Map.of(), false);

    assertFalse(delivered);
    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SESSION_NOT_RELIABLE);
  }

  @Test
  void ioFailure_closesSessionWithServerError() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(userSession.getUserId()).thenReturn(java.util.UUID.randomUUID());
    when(webSocketSession.getId()).thenReturn("ws-io-failure");
    when(webSocketSession.isOpen()).thenReturn(true);
    when(userSession.isSubscribed("/topic/test")).thenReturn(true);
    org.mockito.Mockito.doThrow(new IOException("boom"))
        .when(webSocketSession)
        .sendMessage(any(BinaryMessage.class));

    boolean delivered =
        template.sendToUserSession(userSession, "/topic/test", new byte[] {0x01}, Map.of(), false);

    assertFalse(delivered);
    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SERVER_ERROR);
  }

  @Test
  void returnsFalseWhenNoSubscribedSession() {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UUID userId = UUID.randomUUID();
    // No sessions registered for this user.
    when(registry.getUserSessions(userId)).thenReturn(Set.of());

    boolean delivered =
        template.sendToUser(userId, "/user/queue/history", new byte[] {0x01}, Map.of(), false);

    assertFalse(delivered);
  }

  @Test
  void returnsTrueWhenLocalSubscribedSessionReceivesFrame() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UUID userId = UUID.randomUUID();
    UserSession session = mock(UserSession.class);
    WebSocketSession webSocket = mock(WebSocketSession.class);
    when(session.getWebsocketSession()).thenReturn(webSocket);
    when(session.getUserId()).thenReturn(userId);
    when(webSocket.getId()).thenReturn("ws-subscribed");
    when(webSocket.isOpen()).thenReturn(true);
    // The session has an active subscription to the destination.
    when(session.isSubscribed("/user/queue/history")).thenReturn(true);
    when(registry.getUserSessions(userId)).thenReturn(Set.of(session));

    boolean delivered =
        template.sendToUser(userId, "/user/queue/history", new byte[] {0x01}, Map.of(), false);

    assertTrue(delivered);
    verify(webSocket).sendMessage(any(BinaryMessage.class));
  }

  @Test
  void sendToUserSession_successfulSend_returnsTrue() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.isOpen()).thenReturn(true);
    when(userSession.isSubscribed("/topic/test")).thenReturn(true);

    boolean delivered =
        template.sendToUserSession(userSession, "/topic/test", new byte[] {0x01}, Map.of(), false);

    assertTrue(delivered);
    verify(webSocketSession).sendMessage(any(BinaryMessage.class));
  }

  @Test
  void sendToUserSession_closedSession_returnsFalseWithoutSending() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.isOpen()).thenReturn(false);
    when(userSession.isSubscribed("/topic/test")).thenReturn(true);

    boolean delivered =
        template.sendToUserSession(userSession, "/topic/test", new byte[] {0x01}, Map.of(), false);

    assertFalse(delivered);
    verify(webSocketSession, never()).sendMessage(any(BinaryMessage.class));
    verify(registry, never()).remove(userSession);
  }

  @Test
  void sendToUserSession_unsubscribedSession_returnsFalseWithoutSending() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.isOpen()).thenReturn(true);
    when(userSession.isSubscribed("/topic/test")).thenReturn(false);

    boolean delivered =
        template.sendToUserSession(userSession, "/topic/test", new byte[] {0x01}, Map.of(), false);

    assertFalse(delivered);
    verify(webSocketSession, never()).sendMessage(any(BinaryMessage.class));
    verify(registry, never()).remove(userSession);
  }
}
