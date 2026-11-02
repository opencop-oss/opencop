// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_BACKING_SESSION_ID;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.userSessionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.WebSocketSession;

class WsSessionRegistryTest {

  @Test
  void registerAndGetUserSessionReturnsStoredSession() {
    WsSessionRegistry registry = registry();
    UUID userId = UUID.randomUUID();
    WebSocketSession wsSession = wsSession("ws-1", userId);

    UserSession userSession = register(registry, wsSession);

    assertNotNull(userSession);
    assertEquals(userId, userSession.getUserId());
    assertEquals(userSession, registry.getUserSession(wsSession));
    assertEquals(Set.of(userSession), registry.getUserSessions(userId));
  }

  @Test
  void sessionlessSocketRegistersWithoutABackingSession() {
    @SuppressWarnings("unchecked")
    SessionRepository<Session> repository = mock(SessionRepository.class);
    WsSessionRegistry registry = new WsSessionRegistry(repository, userSessionFactory());
    UUID userId = UUID.randomUUID();

    UserSession session =
        register(
            registry,
            wsSession("ws-dev", userId, null, Instant.parse("2026-07-28T20:00:00Z"), true));

    assertTrue(session.isSessionless());
    assertNull(session.getBackingSessionId());
    assertEquals(Set.of(session), registry.getUserSessions(userId));
  }

  @Test
  void destinationMembershipIsRemovedWithTheSocket() {
    WsSessionRegistry registry = registry();
    UUID userId = UUID.randomUUID();
    WebSocketSession firstSocket = wsSession("ws-1", userId);
    WebSocketSession secondSocket = wsSession("ws-2", userId);
    UserSession first = register(registry, firstSocket);
    UserSession second = register(registry, secondSocket);
    assertTrue(registry.subscribe("/topic/test", first));
    assertTrue(registry.subscribe("/topic/test", second));

    registry.remove(firstSocket);

    assertNull(registry.getUserSession(firstSocket));
    assertNotNull(registry.getUserSession(secondSocket));
    assertEquals(Set.of(second), registry.getDestinationSessions("/topic/test"));
  }

  @Test
  void subscribeWritesBothStructuresAndRefusesUnregisteredSessions() {
    WsSessionRegistry registry = registry();
    UUID userId = UUID.randomUUID();
    UserSession session = register(registry, wsSession("ws-1", userId));

    assertTrue(registry.subscribe("/topic/test", session));
    assertEquals(Set.of(session), registry.getDestinationSessions("/topic/test"));
    // Both writes happen under one monitor hold — the mirror of revoke — so a concurrent
    // revocation sweep can never observe the destination index without the session-side entry.
    assertTrue(session.isSubscribed("/topic/test"));

    // Never registered with the registry: neither structure may be written.
    UserSession stranger = userSessionFactory().create(wsSession("ws-ghost", UUID.randomUUID()));
    assertFalse(registry.subscribe("/topic/test", stranger));
    assertFalse(stranger.isSubscribed("/topic/test"));
    assertEquals(Set.of(session), registry.getDestinationSessions("/topic/test"));
  }

  @Test
  void duplicateDestinationRegistrationReturnsFalse() {
    WsSessionRegistry registry = registry();
    UserSession session = register(registry, wsSession("ws-1", UUID.randomUUID()));

    assertTrue(registry.subscribe("/topic/test", session));

    assertFalse(registry.subscribe("/topic/test", session));
    assertEquals(Set.of(session), registry.getDestinationSessions("/topic/test"));
  }

  @Test
  void revokeClearsBothTheDestinationIndexAndTheSessionBookkeeping() {
    WsSessionRegistry registry = registry();
    UUID userId = UUID.randomUUID();
    UserSession session = register(registry, wsSession("ws-1", userId));
    assertTrue(registry.subscribe("/topic/test", session));

    assertTrue(registry.revoke("/topic/test", session));

    assertTrue(registry.getDestinationSessions("/topic/test").isEmpty());
    // The session-side entry must clear too, or a re-SUBSCRIBE would be silently swallowed by
    // the handler's already-subscribed check instead of being re-evaluated.
    assertFalse(session.isSubscribed("/topic/test"));
    // Idempotent: a second revocation reports nothing removed.
    assertFalse(registry.revoke("/topic/test", session));
  }

  @Test
  void removingAUserPrunesItsDestinationEntries() {
    WsSessionRegistry registry = registry();
    UUID userId = UUID.randomUUID();
    UserSession session = register(registry, wsSession("ws-1", userId));
    assertTrue(registry.subscribe("/topic/test", session));

    registry.remove(userId);

    assertTrue(registry.getUserSessions(userId).isEmpty());
    assertTrue(registry.getDestinationSessions("/topic/test").isEmpty());
    assertFalse(hasDestinationEntry(registry, "/topic/test"));
  }

  @Test
  void rejectsSocketWhenItsBackingSessionDoesNotExist() {
    @SuppressWarnings("unchecked")
    SessionRepository<Session> repository = mock(SessionRepository.class);
    WsSessionRegistry registry = new WsSessionRegistry(repository, userSessionFactory());

    var registration = registry.register(wsSession("ws-expired", UUID.randomUUID()));

    assertFalse(registration.registered());
    assertTrue(registry.getUsersSessions().isEmpty());
  }

  @Test
  void registersSocketWhenItsBackingSessionStillExists() {
    @SuppressWarnings("unchecked")
    SessionRepository<Session> repository = mock(SessionRepository.class);
    when(repository.findById("bff-ws-active")).thenReturn(mock(Session.class));
    WsSessionRegistry registry = new WsSessionRegistry(repository, userSessionFactory());

    var registration = registry.register(wsSession("ws-active", UUID.randomUUID()));

    assertTrue(registration.registered());
  }

  @Test
  void registerRejectsMissingAuthentication() {
    WsSessionRegistry registry = registry();
    WebSocketSession noAuth = mock(WebSocketSession.class);
    when(noAuth.getId()).thenReturn("ws-no-auth");
    when(noAuth.getAttributes()).thenReturn(Map.of());

    assertThrows(IllegalArgumentException.class, () -> registry.register(noAuth));
  }

  private static WebSocketSession wsSession(String sessionId, UUID userId) {
    return wsSession(
        sessionId, userId, "bff-" + sessionId, Instant.parse("2026-07-28T20:00:00Z"), false);
  }

  private static WebSocketSession wsSession(
      String sessionId,
      UUID userId,
      String backingSessionId,
      Instant authenticationDeadline,
      boolean sessionless) {
    WebSocketSession wsSession = mock(WebSocketSession.class);
    when(wsSession.getId()).thenReturn(sessionId);
    var attributes = new HashMap<String, Object>();
    attributes.put(ATTR_AUTH, authToken(userId));
    attributes.put(ATTR_EXP, authenticationDeadline);
    attributes.put(ATTR_SESSIONLESS, sessionless);
    if (backingSessionId != null) {
      attributes.put(ATTR_BACKING_SESSION_ID, backingSessionId);
    }
    when(wsSession.getAttributes()).thenReturn(attributes);
    when(wsSession.isOpen()).thenReturn(true);
    return wsSession;
  }

  private static WsSessionRegistry registry() {
    @SuppressWarnings("unchecked")
    SessionRepository<Session> repository = mock(SessionRepository.class);
    when(repository.findById(anyString())).thenReturn(mock(Session.class));
    return new WsSessionRegistry(repository, userSessionFactory());
  }

  private static UserSession register(
      WsSessionRegistry registry, WebSocketSession webSocketSession) {
    var registration = registry.register(webSocketSession);
    assertTrue(registration.registered());
    return registration.session();
  }

  private static OAuth2AuthenticationToken authToken(UUID userId) {
    OpenCopPrincipal principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId);
    return new OAuth2AuthenticationToken(principal, List.of(), "test-client");
  }

  @SuppressWarnings("unchecked")
  private static boolean hasDestinationEntry(WsSessionRegistry registry, String destination) {
    try {
      Field field = WsSessionRegistry.class.getDeclaredField("destinationSessions");
      field.setAccessible(true);
      ConcurrentMap<String, Set<UserSession>> destinationSessions =
          (ConcurrentMap<String, Set<UserSession>>) field.get(registry);
      return destinationSessions.containsKey(destination);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to inspect destinationSessions", e);
    }
  }
}
