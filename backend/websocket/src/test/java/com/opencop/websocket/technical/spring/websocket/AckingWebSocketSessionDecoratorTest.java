// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.protocolViolationTracker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.adapter.NativeWebSocketSession;

class AckingWebSocketSessionDecoratorTest {

  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int BUFFER_SIZE_LIMIT = 1024 * 1024;

  @Test
  void sendBinaryAwait_jettyNativeCallbackControlsCompletion() throws Exception {
    JettyRecorder recorder = new JettyRecorder();
    NativeWebSocketSession spring =
        nativeSession(true, "jetty-session-id", null, recorder.session, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {1, 2, 3});

    assertThat(future.isDone()).isFalse();
    assertThat(recorder.callbacks).hasSize(1);

    recorder.callbacks.get(0).succeed();

    assertThat(future.get(1, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void sendBinaryAwait_failsWhenNoSupportedNativeAsyncSessionExists() {
    NativeWebSocketSession spring = nativeSession(true, "unsupported", null, null, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {9});

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("native websocket session does not expose an async binary send API");
  }

  @Test
  void sendBinaryAwait_timesOutWhenJettyCallbackNeverFires() {
    JettyRecorder recorder = new JettyRecorder();
    NativeWebSocketSession spring =
        nativeSession(true, "never-callback", null, recorder.session, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, 100, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {1});

    assertThat(recorder.callbacks).hasSize(1);
    assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
        .hasRootCauseInstanceOf(TimeoutException.class)
        .hasRootCauseMessage("websocket binary send timed out after 100 ms");
  }

  @Test
  void sendBinaryAwait_serializesSecondAwaitedSendUntilFirstCompletes() throws Exception {
    JettyRecorder recorder = new JettyRecorder();
    NativeWebSocketSession spring = nativeSession(true, "serialized", null, recorder.session, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> firstFuture = decorator.sendBinaryAwait(new byte[] {1});
    CompletableFuture<Void> secondFuture = decorator.sendBinaryAwait(new byte[] {2});

    assertThat(recorder.callbacks).hasSize(1);
    assertThat(firstFuture.isDone()).isFalse();
    assertThat(secondFuture.isDone()).isFalse();

    recorder.callbacks.get(0).succeed();
    assertThat(firstFuture.get(1, TimeUnit.SECONDS)).isNull();

    assertThat(recorder.callbacks).hasSize(2);
    assertThat(secondFuture.isDone()).isFalse();
    recorder.callbacks.get(1).succeed();
    assertThat(secondFuture.get(1, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void sendBinaryAwait_cancelsTimeoutTaskAfterCallbackCompletion() throws Exception {
    JettyRecorder recorder = new JettyRecorder();
    NativeWebSocketSession spring =
        nativeSession(true, "timeout-cancel", null, recorder.session, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
    ScheduledThreadPoolExecutor scheduler =
        AckingWebSocketSessionDecorator.timeoutSchedulerForTests();

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {1});
    assertThat(recorder.callbacks).hasSize(1);

    recorder.callbacks.get(0).succeed();
    assertThat(future.get(1, TimeUnit.SECONDS)).isNull();

    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
    while (scheduler.getQueue().size() != 0 && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(scheduler.getQueue().size()).isZero();
  }

  @Test
  void sendBinaryAwait_completesNormallyOnJakartaOkResult() throws Exception {
    JakartaRecorder recorder = new JakartaRecorder();
    Session jakarta = recorder.session();
    NativeWebSocketSession spring = nativeSession(true, "jakarta-ok", jakarta, null, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {1, 2, 3});

    assertThat(future.isDone()).isFalse();
    assertThat(recorder.handlers).hasSize(1);
    recorder.handlers.get(0).onResult(new SendResult());

    assertThat(future.get(1, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void sendBinaryAwait_completesExceptionallyOnJakartaFailedResult() {
    JakartaRecorder recorder = new JakartaRecorder();
    Session jakarta = recorder.session();
    NativeWebSocketSession spring = nativeSession(true, "jakarta-fail", jakarta, null, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {4, 5, 6});

    assertThat(recorder.handlers).hasSize(1);
    recorder.handlers.get(0).onResult(new SendResult(new IOException("transport error")));

    assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("transport error");
  }

  @Test
  void sendBinaryAwait_shortCircuitsWhenSessionClosedBeforeSend() {
    JakartaRecorder recorder = new JakartaRecorder();
    Session jakarta = recorder.session();
    NativeWebSocketSession spring = nativeSession(false, "closed-before", jakarta, null, null);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {7});

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("session closed before send");
    assertThat(recorder.handlers).isEmpty();
  }

  @Test
  void sendBinaryAwait_completesExceptionallyWhenSessionClosedDuringSend() {
    JakartaRecorder recorder = new JakartaRecorder();
    Session jakarta = recorder.session();
    List<Boolean> openSequence = List.of(true, true, false);
    NativeWebSocketSession spring =
        nativeSessionWithOpenSequence("closed-during", jakarta, null, null, openSequence);
    AckingWebSocketSessionDecorator decorator =
        new AckingWebSocketSessionDecorator(spring, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

    CompletableFuture<Void> future = decorator.sendBinaryAwait(new byte[] {8, 9});

    assertThat(recorder.handlers).hasSize(1);
    recorder.handlers.get(0).onResult(new SendResult());

    assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("session closed during send");
  }

  @Test
  void userSession_usesAckingDecoratorAndStillDelegatesRegularSendMessage() throws Exception {
    UUID userId = UUID.randomUUID();
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(ATTR_AUTH, authToken(userId));
    attributes.put(ATTR_EXP, Instant.now().plusSeconds(60));
    attributes.put(ATTR_SESSIONLESS, true);
    List<WebSocketMessage<?>> sentMessages = new ArrayList<>();
    attributes.put("sentMessages", sentMessages);

    NativeWebSocketSession nativeSession =
        nativeSession(true, "user-session-id", null, null, attributes);
    UserSession userSession = new UserSession(nativeSession, protocolViolationTracker());

    assertThat(userSession.getWebsocketSession())
        .isInstanceOf(AckingWebSocketSessionDecorator.class);
    BinaryMessage message = new BinaryMessage(new byte[] {9, 8, 7});
    userSession.getWebsocketSession().sendMessage(message);

    assertThat(sentMessages).hasSize(1);
    assertThat(((BinaryMessage) sentMessages.get(0)).getPayload().array()).containsExactly(9, 8, 7);
  }

  @SuppressWarnings("unchecked")
  private static NativeWebSocketSession nativeSession(
      boolean open,
      String id,
      Session jakarta,
      org.eclipse.jetty.websocket.api.Session jetty,
      Map<String, Object> attributes) {
    AtomicBoolean isOpen = new AtomicBoolean(open);
    Map<String, Object> attrs = attributes == null ? Collections.emptyMap() : attributes;
    List<WebSocketMessage<?>> sentMessages =
        (List<WebSocketMessage<?>>) attrs.getOrDefault("sentMessages", new ArrayList<>());

    InvocationHandler handler =
        (proxy, method, args) -> {
          String name = method.getName();
          if (name.equals("isOpen")) {
            return isOpen.get();
          }
          if (name.equals("getId")) {
            return id;
          }
          if (name.equals("getNativeSession")) {
            if (args == null || args.length == 0) {
              return jakarta != null ? jakarta : jetty;
            }
            Class<?> type = (Class<?>) args[0];
            if (type == Session.class) {
              return jakarta;
            }
            if (type == org.eclipse.jetty.websocket.api.Session.class) {
              return jetty;
            }
            return null;
          }
          if (name.equals("sendMessage")) {
            sentMessages.add((WebSocketMessage<?>) args[0]);
            return null;
          }
          if (name.equals("close")) {
            isOpen.set(false);
            return null;
          }
          if (name.equals("getAttributes")) {
            return attrs;
          }
          if (name.equals("getAcceptedProtocol")) {
            return null;
          }
          if (name.equals("getUri")) {
            return null;
          }
          if (name.equals("getHandshakeHeaders")) {
            return null;
          }
          if (name.equals("getExtensions")) {
            return List.of();
          }
          if (name.equals("getLocalAddress") || name.equals("getRemoteAddress")) {
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
            AckingWebSocketSessionDecoratorTest.class.getClassLoader(),
            new Class[] {NativeWebSocketSession.class},
            handler);
  }

  private static NativeWebSocketSession nativeSessionWithOpenSequence(
      String id,
      Session jakarta,
      org.eclipse.jetty.websocket.api.Session jetty,
      Map<String, Object> attributes,
      List<Boolean> openSequence) {
    AtomicInteger index = new AtomicInteger(0);
    InvocationHandler base =
        Proxy.getInvocationHandler(nativeSession(true, id, jakarta, jetty, attributes));
    InvocationHandler handler =
        (proxy, method, args) -> {
          if (method.getName().equals("isOpen")) {
            int i = index.getAndUpdate(value -> Math.min(value + 1, openSequence.size() - 1));
            return openSequence.get(i);
          }
          return base.invoke(proxy, method, args);
        };
    return (NativeWebSocketSession)
        Proxy.newProxyInstance(
            AckingWebSocketSessionDecoratorTest.class.getClassLoader(),
            new Class[] {NativeWebSocketSession.class},
            handler);
  }

  private static OAuth2AuthenticationToken authToken(UUID userId) {
    OpenCopPrincipal principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId);
    return new OAuth2AuthenticationToken(principal, List.of(), "test-client");
  }

  private static class JettyRecorder {
    final List<org.eclipse.jetty.websocket.api.Callback> callbacks = new CopyOnWriteArrayList<>();
    final org.eclipse.jetty.websocket.api.Session session =
        (org.eclipse.jetty.websocket.api.Session)
            Proxy.newProxyInstance(
                AckingWebSocketSessionDecoratorTest.class.getClassLoader(),
                new Class[] {org.eclipse.jetty.websocket.api.Session.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("sendBinary")) {
                    callbacks.add((org.eclipse.jetty.websocket.api.Callback) args[1]);
                    return null;
                  }
                  return null;
                });
  }

  private static class JakartaRecorder {
    final List<SendHandler> handlers = new CopyOnWriteArrayList<>();

    Session session() {
      RemoteEndpoint.Async async =
          (RemoteEndpoint.Async)
              Proxy.newProxyInstance(
                  AckingWebSocketSessionDecoratorTest.class.getClassLoader(),
                  new Class[] {RemoteEndpoint.Async.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("sendBinary")) {
                      handlers.add((SendHandler) args[1]);
                      return null;
                    }
                    return null;
                  });

      return (Session)
          Proxy.newProxyInstance(
              AckingWebSocketSessionDecoratorTest.class.getClassLoader(),
              new Class[] {Session.class},
              (proxy, method, args) -> {
                if (method.getName().equals("getAsyncRemote")) {
                  return async;
                }
                return null;
              });
    }
  }
}
