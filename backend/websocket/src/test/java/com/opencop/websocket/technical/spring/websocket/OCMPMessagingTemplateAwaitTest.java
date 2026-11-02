// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.protocolViolationTracker;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.userSessionFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.websocket.api.Callback;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;

class OCMPMessagingTemplateAwaitTest {

  @Test
  void noSessionForUser_returnsNoSessionImmediatelyWithoutSend() throws Exception {
    UUID userId = UUID.randomUUID();
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);

    CompletableFuture<SendAttemptResult> future =
        template.sendToUserAwait(userId, "/user/queue/history", new byte[] {1}, Map.of(), false);

    assertThat(future.isDone()).isTrue();
    assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(SendAttemptResult.NO_SESSION);
  }

  @Test
  void oneSessionSucceedsAndOneNeverCallbacks_returnsDeliveredAndCleansTimedOutSession()
      throws Exception {
    UUID userId = UUID.randomUUID();
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();

    JettyRecorder sessionAJetty = new JettyRecorder();
    UserSession sessionA = newUserSession("session-a", userId, sessionAJetty.session, 100);
    sessionA.addDestination("/user/queue/history");

    JettyRecorder sessionBJetty = new JettyRecorder();
    UserSession sessionB = newUserSession("session-b", userId, sessionBJetty.session, 100);
    sessionB.addDestination("/user/queue/history");

    registry.put(userId, Set.of(sessionA, sessionB));
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);

    CompletableFuture<SendAttemptResult> result =
        template.sendToUserAwait(userId, "/user/queue/history", new byte[] {7}, Map.of(), false);

    assertThat(sessionAJetty.callbacks).hasSize(1);
    assertThat(sessionBJetty.callbacks).hasSize(1);

    sessionAJetty.callbacks.get(0).succeed();

    assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo(SendAttemptResult.DELIVERED);
    assertThat(killer.cancelledSessions).containsExactly(sessionB);
  }

  @Test
  void twoSessions_bothFail_futureCompletesExceptionallyWithIoException() {
    UUID userId = UUID.randomUUID();
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();

    JettyRecorder sessionAJetty = new JettyRecorder();
    UserSession sessionA = newUserSession("session-a-fail", userId, sessionAJetty.session, 500);
    sessionA.addDestination("/user/queue/history");

    JettyRecorder sessionBJetty = new JettyRecorder();
    UserSession sessionB = newUserSession("session-b-fail", userId, sessionBJetty.session, 500);
    sessionB.addDestination("/user/queue/history");

    registry.put(userId, Set.of(sessionA, sessionB));
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);

    CompletableFuture<SendAttemptResult> result =
        template.sendToUserAwait(userId, "/user/queue/history", new byte[] {1}, Map.of(), false);

    assertThat(sessionAJetty.callbacks).hasSize(1);
    assertThat(sessionBJetty.callbacks).hasSize(1);

    sessionAJetty.callbacks.get(0).fail(new IOException("A failed"));
    sessionBJetty.callbacks.get(0).fail(new IOException("B failed"));

    assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("A failed");
  }

  @Test
  void bothSessionsNeverCallback_completeExceptionallyWithTimeoutAndCleanupBoth() {
    UUID userId = UUID.randomUUID();
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();

    JettyRecorder sessionAJetty = new JettyRecorder();
    UserSession sessionA = newUserSession("session-a-timeout", userId, sessionAJetty.session, 100);
    sessionA.addDestination("/user/queue/history");

    JettyRecorder sessionBJetty = new JettyRecorder();
    UserSession sessionB = newUserSession("session-b-timeout", userId, sessionBJetty.session, 100);
    sessionB.addDestination("/user/queue/history");

    registry.put(userId, Set.of(sessionA, sessionB));
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);

    CompletableFuture<SendAttemptResult> result =
        template.sendToUserAwait(userId, "/user/queue/history", new byte[] {7}, Map.of(), false);

    assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .rootCause()
        .isInstanceOf(TimeoutException.class);
    assertThat(killer.cancelledSessions).containsExactlyInAnyOrder(sessionA, sessionB);
  }

  @Test
  void sessionNotSubscribedToDestination_returnsNoSession() throws Exception {
    UUID userId = UUID.randomUUID();
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();

    JettyRecorder sessionJetty = new JettyRecorder();
    UserSession session = newUserSession("session-c", userId, sessionJetty.session, 100);
    registry.put(userId, Set.of(session));

    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);

    CompletableFuture<SendAttemptResult> result =
        template.sendToUserAwait(userId, "/user/queue/history", new byte[] {1}, Map.of(), false);

    assertThat(result.isDone()).isTrue();
    assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo(SendAttemptResult.NO_SESSION);
    assertThat(sessionJetty.callbacks).isEmpty();
  }

  @Test
  void exceptionalCompletion_cleanupRunsOnSessionCleanupThread() throws Exception {
    UUID userId = UUID.randomUUID();
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();

    JettyRecorder sessionJetty = new JettyRecorder();
    UserSession session = newUserSession("session-d", userId, sessionJetty.session, 500);
    session.addDestination("/user/queue/history");
    registry.put(userId, Set.of(session));

    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);
    CompletableFuture<SendAttemptResult> result =
        template.sendToUserAwait(userId, "/user/queue/history", new byte[] {1}, Map.of(), false);

    assertThat(sessionJetty.callbacks).hasSize(1);
    Thread transportThread =
        new Thread(
            () -> sessionJetty.callbacks.get(0).fail(new IOException("transport error")),
            "transport-callback-test");
    transportThread.start();
    transportThread.join(2_000);

    assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(IOException.class);

    long deadline = System.currentTimeMillis() + 2_000;
    while (killer.lastCancelThreadName == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    assertThat(killer.lastCancelThreadName).startsWith("ws-cleanup-");
    assertThat(killer.lastCancelThreadName).isNotEqualTo("transport-callback-test");
  }

  @Test
  void nonAckingWrapper_returnsFailedFutureWithIllegalStateException() {
    StubRegistry registry = new StubRegistry();
    RecordingSessionExpiryKiller killer = new RecordingSessionExpiryKiller();
    OCMPMessagingTemplate template = new OCMPMessagingTemplate(registry, killer);

    UUID userId = UUID.randomUUID();
    UserSession nonAckingSession = nonAckingUserSession("non-acking", userId);

    CompletableFuture<Void> result =
        template.sendToSessionAwait(
            nonAckingSession, "/user/queue/history", new byte[] {1}, Map.of(), false);

    assertThatThrownBy(() -> result.get(1, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected AckingWebSocketSessionDecorator for awaited websocket send");
  }

  private static UserSession newUserSession(
      String sessionId,
      UUID userId,
      org.eclipse.jetty.websocket.api.Session jetty,
      int sendLimitMs) {
    NativeWebSocketSession spring = nativeSession(true, sessionId, userId, jetty);
    return new UserSession(
        spring, sendLimitMs, UserSession.BUFFER_SIZE_LIMIT, protocolViolationTracker());
  }

  private static UserSession nonAckingUserSession(String sessionId, UUID userId) {
    NativeWebSocketSession base = nativeSession(true, sessionId, userId, null);
    WebSocketSession nonAcking =
        (WebSocketSession)
            Proxy.newProxyInstance(
                OCMPMessagingTemplateAwaitTest.class.getClassLoader(),
                new Class[] {WebSocketSession.class},
                (proxy, method, args) -> {
                  String name = method.getName();
                  if (name.equals("getId")) {
                    return sessionId;
                  }
                  if (name.equals("isOpen")) {
                    return true;
                  }
                  if (name.equals("getAttributes")) {
                    return Map.of();
                  }
                  return null;
                });
    return new UserSession(base, protocolViolationTracker()) {
      @Override
      public WebSocketSession getWebsocketSession() {
        return nonAcking;
      }
    };
  }

  private static NativeWebSocketSession nativeSession(
      boolean open, String sessionId, UUID userId, org.eclipse.jetty.websocket.api.Session jetty) {
    Principal principal = () -> "principal-" + sessionId;
    Map<String, Object> attrs =
        Map.of(
            ATTR_AUTH, authToken(userId),
            ATTR_EXP, Instant.now().plusSeconds(60),
            ATTR_SESSIONLESS, true);
    InvocationHandler handler =
        (proxy, method, args) -> {
          String name = method.getName();
          if (name.equals("isOpen")) {
            return open;
          }
          if (name.equals("getId")) {
            return sessionId;
          }
          if (name.equals("getPrincipal")) {
            return principal;
          }
          if (name.equals("getAttributes")) {
            return attrs;
          }
          if (name.equals("getNativeSession")) {
            if (args != null && args.length == 1) {
              Class<?> type = (Class<?>) args[0];
              if (type == jakarta.websocket.Session.class) {
                return null;
              }
              if (type == org.eclipse.jetty.websocket.api.Session.class) {
                return jetty;
              }
            }
            return null;
          }
          if (name.equals("close")) {
            return null;
          }
          if (name.equals("getExtensions")) {
            return List.of();
          }
          if (name.equals("getHandshakeHeaders")) {
            return null;
          }
          if (name.equals("getLocalAddress") || name.equals("getRemoteAddress")) {
            return null;
          }
          if (name.equals("getAcceptedProtocol")) {
            return null;
          }
          if (name.equals("getTextMessageSizeLimit") || name.equals("getBinaryMessageSizeLimit")) {
            return 0;
          }
          if (name.equals("setTextMessageSizeLimit") || name.equals("setBinaryMessageSizeLimit")) {
            return null;
          }
          return null;
        };
    return (NativeWebSocketSession)
        Proxy.newProxyInstance(
            OCMPMessagingTemplateAwaitTest.class.getClassLoader(),
            new Class[] {NativeWebSocketSession.class},
            handler);
  }

  private static OAuth2AuthenticationToken authToken(UUID userId) {
    OpenCopPrincipal openCopPrincipal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId);
    return new OAuth2AuthenticationToken(openCopPrincipal, List.of(), "test-client");
  }

  private static class StubRegistry extends WsSessionRegistry {
    private final Map<UUID, Set<UserSession>> byUser =
        new java.util.concurrent.ConcurrentHashMap<>();

    StubRegistry() {
      super(null, userSessionFactory());
    }

    void put(UUID userId, Set<UserSession> sessions) {
      byUser.put(userId, new HashSet<>(sessions));
    }

    @Override
    public Set<UserSession> getUserSessions(UUID userId) {
      return byUser.getOrDefault(userId, Set.of());
    }
  }

  private static class RecordingSessionExpiryKiller extends SessionExpiryKiller {
    final List<UserSession> cancelledSessions = new CopyOnWriteArrayList<>();
    volatile String lastCancelThreadName;

    RecordingSessionExpiryKiller() {
      super(
          new NoopTaskScheduler(),
          new NoopTaskScheduler(),
          new WsSessionRegistry(null, userSessionFactory()));
    }

    @Override
    public void closeAndCleanup(UserSession session, CloseStatus status) {
      cancelledSessions.add(session);
      lastCancelThreadName = Thread.currentThread().getName();
    }
  }

  private static class NoopTaskScheduler implements TaskScheduler {
    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      return null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, java.time.Duration period) {
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) {
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, java.time.Duration delay) {
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) {
      return null;
    }
  }

  private static class JettyRecorder {
    final List<Callback> callbacks = new ArrayList<>();
    final org.eclipse.jetty.websocket.api.Session session =
        (org.eclipse.jetty.websocket.api.Session)
            Proxy.newProxyInstance(
                OCMPMessagingTemplateAwaitTest.class.getClassLoader(),
                new Class[] {org.eclipse.jetty.websocket.api.Session.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("sendBinary")) {
                    callbacks.add((Callback) args[1]);
                  }
                  return null;
                });
  }
}
