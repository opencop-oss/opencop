// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_BACKING_SESSION_ID;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.protocolViolationTracker;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.userSessionFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import com.opencop.websocket.ports.business.usecase.event.LiveEventAccessAuthorizer;
import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import com.opencop.websocket.technical.gateway.websocket.WebSocketEventListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class OCMPWebSocketHandlerTest {

  private static final Instant AUTHENTICATION_DEADLINE = Instant.parse("2099-07-28T20:00:00Z");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void heartbeatForUnregisteredSessionClosesTheRawSessionWithoutThrowing() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    WebSocketSession session = authenticatedSession("ws-heartbeat", UUID.randomUUID());
    when(registry.getUserSession(session)).thenReturn(null);

    BinaryMessage heartbeat =
        new BinaryMessage(
            SocketMessage.newBuilder().setType(SocketMessage.Type.HEARTBEAT).build().toByteArray());

    assertDoesNotThrow(() -> handler.handleBinaryMessage(session, heartbeat));

    verify(session).close(CloseStatus.SERVER_ERROR);
    verify(sessionExpiryKiller, never())
        .scheduleClose(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void connectionPublishesTheSessionBeforeSchedulingHeartbeatAndAuthenticationExpiry()
      throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession session = authenticatedSession("ws-established", UUID.randomUUID());
    UserSession userSession = new UserSession(session, protocolViolationTracker());
    when(registry.register(session))
        .thenReturn(new WsSessionRegistry.Registration(userSession, true));

    handler.afterConnectionEstablished(session);

    var calls = inOrder(registry, sessionExpiryKiller);
    calls.verify(registry).register(session);
    calls
        .verify(sessionExpiryKiller)
        .scheduleClose(
            org.mockito.ArgumentMatchers.any(UserSession.class),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.eq(0L),
            org.mockito.ArgumentMatchers.eq(SessionExpiryKiller.HEARTBEAT));
    calls
        .verify(sessionExpiryKiller)
        .scheduleClose(
            org.mockito.ArgumentMatchers.any(UserSession.class),
            org.mockito.ArgumentMatchers.eq(AUTHENTICATION_DEADLINE),
            org.mockito.ArgumentMatchers.eq(0L),
            org.mockito.ArgumentMatchers.eq(SessionExpiryKiller.AUTH));
  }

  @Test
  void connectionCloseDuringFirstExpirySchedulingCannotPublishOrLeakTheSession() throws Exception {
    WsSessionRegistry registry = activeRegistry();
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
    SessionExpiryKiller sessionExpiryKiller =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            mock(LiveEventAccessAuthorizer.class),
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession session =
        authenticatedSession("ws-close-during-registration", UUID.randomUUID());
    doAnswer(
            invocation -> {
              handler.afterConnectionClosed(session, CloseStatus.NORMAL);
              return heartbeatFuture;
            })
        .when(heartbeatScheduler)
        .schedule(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            org.mockito.ArgumentMatchers.any(Instant.class));

    handler.afterConnectionEstablished(session);

    verify(heartbeatFuture).cancel(false);
    verify(session).close(CloseStatus.NORMAL);
    verify(authScheduler, never())
        .schedule(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            org.mockito.ArgumentMatchers.any(Instant.class));
    assertThat(registry.getUsersSessions()).isEmpty();
  }

  @Test
  void connectionClosedBeforeRegistrationCannotBePublishedOrScheduled() throws Exception {
    WsSessionRegistry registry = activeRegistry();
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    SessionExpiryKiller sessionExpiryKiller =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            mock(LiveEventAccessAuthorizer.class),
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession session =
        authenticatedSession("ws-closed-before-registration", UUID.randomUUID());

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    when(session.isOpen()).thenReturn(false);
    handler.afterConnectionEstablished(session);

    assertThat(registry.getUsersSessions()).isEmpty();
    verify(heartbeatScheduler, never())
        .schedule(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            org.mockito.ArgumentMatchers.any(Instant.class));
    verify(authScheduler, never())
        .schedule(
            org.mockito.ArgumentMatchers.any(Runnable.class),
            org.mockito.ArgumentMatchers.any(Instant.class));
  }

  @Test
  void heartbeatForRegisteredSessionReschedulesHeartbeatExpiry() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    WebSocketSession session = authenticatedSession("ws-registered-heartbeat", UUID.randomUUID());
    UserSession userSession = new UserSession(session, protocolViolationTracker());
    when(registry.getUserSession(session)).thenReturn(userSession);
    BinaryMessage heartbeat =
        new BinaryMessage(
            SocketMessage.newBuilder().setType(SocketMessage.Type.HEARTBEAT).build().toByteArray());

    handler.handleBinaryMessage(session, heartbeat);

    verify(sessionExpiryKiller)
        .scheduleClose(
            org.mockito.ArgumentMatchers.same(userSession),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.eq(0L),
            org.mockito.ArgumentMatchers.eq(SessionExpiryKiller.HEARTBEAT));
  }

  @Test
  void malformedBinaryFrameClosesSessionAsBadDataWithoutThrowing() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    WebSocketSession session = authenticatedSession("ws-malformed", UUID.randomUUID());

    assertDoesNotThrow(
        () ->
            handler.handleBinaryMessage(session, new BinaryMessage(new byte[] {0x01, 0x02, 0x03})));

    verify(session).close(CloseStatus.BAD_DATA);
  }

  @Test
  void textFrameReturnsGenericDirectErrorWithoutMutationAndKeepsSessionOpen() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession rawSession = authenticatedSession("ws-text", UUID.randomUUID());
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    when(registry.getUserSession(rawSession)).thenReturn(userSession);

    handler.handleTextMessage(rawSession, new TextMessage("private client content"));

    var sent = ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
    verify(rawSession).sendMessage(sent.capture());
    var error = SocketMessage.parseFrom(((BinaryMessage) sent.getValue()).getPayload());
    assertThat(error.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(error.getDestination()).isEmpty();
    assertThat(error.getHeadersMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("code", "UNSUPPORTED_MESSAGE_TYPE", "operation", "TEXT"));
    assertThat(error.getPayload().toStringUtf8()).isEqualTo("Text messages not supported");
    assertThat(error.toString()).doesNotContain("private client content");
    verify(rawSession, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    verify(liveEventAccessAuthorizer, never())
        .userCanSubscribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(registry, never())
        .subscribe(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(UserSession.class));
    verify(listener, never())
        .onSubscribe(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void textFrameAtLimitOneClosesWithoutSendingAnError() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            mock(LiveEventAccessAuthorizer.class),
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession rawSession = authenticatedSession("ws-text-limit-one", UUID.randomUUID());
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(1, Duration.ofMinutes(1)));
    when(registry.getUserSession(rawSession)).thenReturn(userSession);

    handler.handleTextMessage(rawSession, new TextMessage("private client content"));

    verify(rawSession, never()).sendMessage(org.mockito.ArgumentMatchers.any(BinaryMessage.class));
    verify(sessionExpiryKiller)
        .closeAndCleanup(
            userSession,
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
  }

  @Test
  void transportErrorsWithNullMessagesStillCloseTheSessionWithoutThrowing() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    WebSocketSession session = authenticatedSession("ws-transport", UUID.randomUUID());
    UserSession userSession = mock(UserSession.class);
    when(registry.getUserSession(session)).thenReturn(userSession);

    assertDoesNotThrow(() -> handler.handleTransportError(session, new IllegalStateException()));

    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SERVER_ERROR);
    verify(session, never()).close(CloseStatus.SERVER_ERROR);
  }

  @Test
  void connectionCloseDelegatesToTheCentralCleanupPath() {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession session = authenticatedSession("ws-closed", UUID.randomUUID());
    UserSession userSession = mock(UserSession.class);
    when(registry.getUserSession(session)).thenReturn(userSession);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.NORMAL);
    verify(registry, never()).remove(session);
  }

  @Test
  void topicSubscribeUsesLiveEventAccessAuthorizerBeforeRegisteringTopic() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-subscribe", userId);
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    String destination = "/topic/queue/event/" + destinationId;

    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    when(liveEventAccessAuthorizer.userCanSubscribe(userId, destinationId)).thenReturn(true);
    when(registry.subscribe(destination, userSession))
        .thenAnswer(ignored -> userSession.addDestination(destination));

    BinaryMessage subscribe =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination(destination)
                .build()
                .toByteArray());

    assertDoesNotThrow(() -> handler.handleBinaryMessage(rawSession, subscribe));
    assertDoesNotThrow(() -> handler.handleBinaryMessage(rawSession, subscribe));

    verify(liveEventAccessAuthorizer, times(1)).userCanSubscribe(userId, destinationId);
    verify(registry).subscribe(destination, userSession);
    verify(listener).onSubscribe(destination, userId);
  }

  @Test
  void topicSubscribeDeniedByLiveEventAccessAuthorizerDoesNotRegisterTopic() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-subscribe-denied", userId);
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(10, Duration.ofMinutes(1)));
    String destination = "/topic/queue/event/" + destinationId;

    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    when(liveEventAccessAuthorizer.userCanSubscribe(userId, destinationId)).thenReturn(false);

    BinaryMessage subscribe =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination(destination)
                .build()
                .toByteArray());

    assertDoesNotThrow(() -> handler.handleBinaryMessage(rawSession, subscribe));

    verify(liveEventAccessAuthorizer, times(1)).userCanSubscribe(userId, destinationId);
    verify(registry, never()).subscribe(destination, userSession);
    verify(listener, never()).onSubscribe(destination, userId);
    verify(rawSession, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(BinaryMessage.class));
    verify(rawSession, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    verify(sessionExpiryKiller, never())
        .closeAndCleanup(
            org.mockito.ArgumentMatchers.any(UserSession.class),
            org.mockito.ArgumentMatchers.any(CloseStatus.class));
  }

  @Test
  void repeatedDeniedTopicSubscribesReachViolationThresholdAndCloseTheSocket() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-subscribe-denied-threshold", userId);
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(2, Duration.ofMinutes(1)));
    String destination = "/topic/queue/event/" + destinationId;

    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    when(liveEventAccessAuthorizer.userCanSubscribe(userId, destinationId)).thenReturn(false);

    BinaryMessage subscribe =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination(destination)
                .build()
                .toByteArray());

    handler.handleBinaryMessage(rawSession, subscribe);
    handler.handleBinaryMessage(rawSession, subscribe);

    verify(rawSession, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(BinaryMessage.class));
    verify(sessionExpiryKiller)
        .closeAndCleanup(
            userSession,
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
    verify(registry, never()).subscribe(destination, userSession);
    verify(listener, never()).onSubscribe(destination, userId);
  }

  @Test
  void deniedTopicSubscribesShareTheBudgetWithOtherProtocolViolations() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-subscribe-denied-shared", userId);
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(2, Duration.ofMinutes(1)));

    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    when(liveEventAccessAuthorizer.userCanSubscribe(userId, destinationId)).thenReturn(false);

    handler.handleBinaryMessage(
        rawSession,
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination("/bad")
                .build()
                .toByteArray()));
    handler.handleBinaryMessage(
        rawSession,
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination("/topic/queue/event/" + destinationId)
                .build()
                .toByteArray()));

    verify(rawSession, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(BinaryMessage.class));
    verify(sessionExpiryKiller)
        .closeAndCleanup(
            userSession,
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
  }

  @Test
  void invalidSubscribeReturnsDirectTypedErrorBeforeAuthorizationOrMutation() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    UUID userId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-invalid-subscribe", userId);
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    var submitted =
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SUBSCRIBE)
            .setDestination("/user/queue/errors")
            .build();

    handler.handleBinaryMessage(rawSession, new BinaryMessage(submitted.toByteArray()));

    var sent = ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
    verify(rawSession).sendMessage(sent.capture());
    var binary = (BinaryMessage) sent.getValue();
    var error = SocketMessage.parseFrom(binary.getPayload());
    assertThat(error.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(error.getDestination()).isEmpty();
    assertThat(error.getCompressed()).isFalse();
    assertThat(error.getHeadersMap())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "code",
                "INVALID_DESTINATION",
                "operation",
                "SUBSCRIBE",
                "rejected-destination",
                "/user/queue/errors"));
    assertThat(error.getPayload().toStringUtf8()).isEqualTo("Invalid destination");
    verify(liveEventAccessAuthorizer, never())
        .userCanSubscribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(registry, never())
        .subscribe(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(UserSession.class));
    verify(listener, never())
        .onSubscribe(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void validHeartbeatDoesNotResetViolationsBeforeThresholdClosure() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession rawSession = authenticatedSession("ws-threshold", UUID.randomUUID());
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(2, Duration.ofMinutes(1)));
    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    var invalid =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination("/bad")
                .build()
                .toByteArray());

    handler.handleBinaryMessage(rawSession, invalid);
    handler.handleBinaryMessage(
        rawSession,
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.HEARTBEAT)
                .build()
                .toByteArray()));
    handler.handleBinaryMessage(rawSession, invalid);

    verify(rawSession, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(BinaryMessage.class));
    verify(sessionExpiryKiller)
        .closeAndCleanup(
            userSession,
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
    verify(liveEventAccessAuthorizer, never())
        .userCanSubscribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(registry, never())
        .subscribe(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(UserSession.class));
  }

  @Test
  void delayedCallbackAfterCleanupCannotMakeAnotherThresholdDecision() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            mock(LiveEventAccessAuthorizer.class),
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession rawSession = authenticatedSession("ws-cleaned-threshold", UUID.randomUUID());
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(1, Duration.ofMinutes(1)));
    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    doAnswer(
            invocation -> {
              userSession.clearProtocolState();
              return null;
            })
        .when(sessionExpiryKiller)
        .closeAndCleanup(
            org.mockito.ArgumentMatchers.same(userSession),
            org.mockito.ArgumentMatchers.any(CloseStatus.class));
    var invalid =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination("/bad")
                .build()
                .toByteArray());

    handler.handleBinaryMessage(rawSession, invalid);
    handler.handleBinaryMessage(rawSession, invalid);

    verify(sessionExpiryKiller, times(1))
        .closeAndCleanup(
            userSession,
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
    verify(rawSession, never()).sendMessage(org.mockito.ArgumentMatchers.any(BinaryMessage.class));
  }

  @Test
  void authorizationFinishingAfterCleanupCannotRecreateSubscriptionState() throws Exception {
    WsSessionRegistry registry = activeRegistry();
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller =
        new SessionExpiryKiller(mock(TaskScheduler.class), mock(TaskScheduler.class), registry);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    UUID userId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    String destination = "/topic/queue/event/" + destinationId;
    WebSocketSession rawSession = authenticatedSession("ws-delayed-authorization", userId);
    UserSession userSession = registry.register(rawSession).session();
    var authorizationStarted = new CountDownLatch(1);
    var releaseAuthorization = new CountDownLatch(1);
    var handlerFailure = new AtomicReference<Throwable>();
    when(liveEventAccessAuthorizer.userCanSubscribe(userId, destinationId))
        .thenAnswer(
            _ -> {
              authorizationStarted.countDown();
              if (!releaseAuthorization.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release authorization");
              }
              return true;
            });
    var subscribe =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination(destination)
                .build()
                .toByteArray());
    Thread handlerThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    handler.handleBinaryMessage(rawSession, subscribe);
                  } catch (Throwable failure) {
                    handlerFailure.set(failure);
                  }
                });

    assertThat(authorizationStarted.await(2, TimeUnit.SECONDS)).isTrue();
    sessionExpiryKiller.closeAndCleanup(
        userSession,
        new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
    releaseAuthorization.countDown();
    handlerThread.join(TimeUnit.SECONDS.toMillis(5));

    assertThat(handlerThread.isAlive()).isFalse();
    assertThat(handlerFailure.get()).isNull();
    assertThat(userSession.isSubscribed(destination)).isFalse();
    assertThat(registry.getDestinationSessions(destination)).isEmpty();
    verify(listener, never()).onSubscribe(destination, userId);
  }

  @Test
  void cleanupCompletesWhileAnAuthorizedSubscriptionCallbackIsBlocked() throws Exception {
    WsSessionRegistry registry = activeRegistry();
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SessionExpiryKiller sessionExpiryKiller =
        new SessionExpiryKiller(mock(TaskScheduler.class), mock(TaskScheduler.class), registry);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    UUID userId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    String destination = "/topic/queue/event/" + destinationId;
    WebSocketSession rawSession = authenticatedSession("ws-blocked-listener", userId);
    UserSession userSession = registry.register(rawSession).session();
    var listenerStarted = new CountDownLatch(1);
    var releaseListener = new CountDownLatch(1);
    var handlerFailure = new AtomicReference<Throwable>();
    var cleanupFailure = new AtomicReference<Throwable>();
    when(liveEventAccessAuthorizer.userCanSubscribe(userId, destinationId)).thenReturn(true);
    doAnswer(
            _ -> {
              listenerStarted.countDown();
              if (!releaseListener.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release listener");
              }
              return null;
            })
        .when(listener)
        .onSubscribe(destination, userId);
    var subscribe =
        new BinaryMessage(
            SocketMessage.newBuilder()
                .setType(SocketMessage.Type.SUBSCRIBE)
                .setDestination(destination)
                .build()
                .toByteArray());
    Thread handlerThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    handler.handleBinaryMessage(rawSession, subscribe);
                  } catch (Throwable failure) {
                    handlerFailure.set(failure);
                  }
                });

    assertThat(listenerStarted.await(2, TimeUnit.SECONDS)).isTrue();
    Thread cleanupThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    sessionExpiryKiller.closeAndCleanup(
                        userSession,
                        new CloseStatus(
                            CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded"));
                  } catch (Throwable failure) {
                    cleanupFailure.set(failure);
                  }
                });

    cleanupThread.join(TimeUnit.SECONDS.toMillis(2));
    try {
      assertThat(cleanupThread.isAlive()).isFalse();
      assertThat(cleanupFailure.get()).isNull();
      assertThat(userSession.isSubscribed(destination)).isFalse();
      assertThat(registry.getDestinationSessions(destination)).isEmpty();
    } finally {
      releaseListener.countDown();
      handlerThread.join(TimeUnit.SECONDS.toMillis(5));
      cleanupThread.join(TimeUnit.SECONDS.toMillis(5));
    }
    assertThat(handlerThread.isAlive()).isFalse();
    assertThat(handlerFailure.get()).isNull();
  }

  @Test
  void bufferedBacklogForAClosedUnregisteredSessionWarnsOnceAndClosesOnce() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            mock(LiveEventAccessAuthorizer.class),
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            mock(SessionExpiryKiller.class),
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession rawSession = authenticatedSession("ws-backlog", UUID.randomUUID());
    when(registry.getUserSession(rawSession)).thenReturn(null);
    AtomicBoolean open = new AtomicBoolean(true);
    when(rawSession.isOpen()).thenAnswer(_ -> open.get());
    doAnswer(
            invocation -> {
              open.set(false);
              return null;
            })
        .when(rawSession)
        .close(any(CloseStatus.class));
    BinaryMessage heartbeat =
        new BinaryMessage(
            SocketMessage.newBuilder().setType(SocketMessage.Type.HEARTBEAT).build().toByteArray());
    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OCMPWebSocketHandler.class);
    var originalLevel = logger.getLevel();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);

    try {
      for (int i = 0; i < 50; i++) {
        handler.handleBinaryMessage(rawSession, heartbeat);
      }
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    var unregisteredLogs =
        appender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("unregistered session"))
            .toList();
    assertThat(unregisteredLogs).hasSize(50);
    assertThat(unregisteredLogs)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .hasSize(1)
        .first()
        .extracting(ILoggingEvent::getFormattedMessage)
        .isEqualTo(
            "Received HEARTBEAT frame for unregistered session ws-backlog. Closing session.");
    assertThat(unregisteredLogs).filteredOn(event -> event.getLevel() == Level.DEBUG).hasSize(49);
    verify(rawSession, times(1)).close(CloseStatus.SERVER_ERROR);
  }

  @Test
  void violationLogsUseExpectedLevelsAndApprovedMetadataOnly() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            mock(LiveEventAccessAuthorizer.class),
            mock(WatchboxSubscribeAuthorizer.class),
            mock(WebSocketEventListener.class),
            sessionExpiryKiller,
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);
    WebSocketSession rawSession = authenticatedSession("ws-logging", UUID.randomUUID());
    UserSession userSession =
        new UserSession(rawSession, new ProtocolViolationTracker(3, Duration.ofMinutes(1)));
    when(registry.getUserSession(rawSession)).thenReturn(userSession);
    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OCMPWebSocketHandler.class);
    var originalLevel = logger.getLevel();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);

    try {
      handler.handleTextMessage(rawSession, new TextMessage("private text content"));
      handler.handleBinaryMessage(
          rawSession,
          new BinaryMessage(
              SocketMessage.newBuilder()
                  .setType(SocketMessage.Type.SUBSCRIBE)
                  .setDestination("/private/destination")
                  .build()
                  .toByteArray()));
      handler.handleBinaryMessage(
          rawSession,
          new BinaryMessage(
              SocketMessage.newBuilder()
                  .setType(SocketMessage.Type.SEND)
                  .setPayload(com.google.protobuf.ByteString.copyFromUtf8("private payload"))
                  .build()
                  .toByteArray()));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    var violationLogs =
        appender.list.stream()
            .filter(event -> event.getFormattedMessage().startsWith("Protocol violation"))
            .toList();
    assertThat(violationLogs)
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.INFO, Level.DEBUG, Level.WARN);
    assertThat(violationLogs)
        .extracting(ILoggingEvent::getFormattedMessage)
        .allSatisfy(
            message ->
                assertThat(message)
                    .doesNotContain(
                        "private text content", "/private/destination", "private payload"));
  }

  @Test
  void unknownTopicFamilyIsRejectedAsUnknownTopicAndNeverRegistered() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            mock(SessionExpiryKiller.class),
            meterRegistry,
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-unknown-topic", userId);
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    String destination = "/topic/mystery/" + UUID.randomUUID();
    when(registry.getUserSession(rawSession)).thenReturn(userSession);

    handler.handleBinaryMessage(rawSession, subscribeFrame(destination));

    SocketMessage nack = capturedNack(rawSession);
    assertThat(nack.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(nack.getDestination()).isEmpty();
    assertThat(nack.getHeadersMap())
        .containsEntry("code", "UNKNOWN_TOPIC")
        .containsEntry("operation", "SUBSCRIBE")
        .containsEntry("rejected-destination", destination);
    verify(registry, never())
        .subscribe(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    verify(listener, never()).onSubscribe(destination, userId);
    verify(rawSession, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    assertThat(
            meterRegistry.counter("opencop.websocket.rejections", "code", "UNKNOWN_TOPIC").count())
        .isEqualTo(1.0);
  }

  @Test
  void liveEventDestinationWithExtraPathSegmentsIsRejectedAsUnknownTopic() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            mock(SessionExpiryKiller.class),
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-nested-live-event", userId);
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    // Regression pin: the gate previously matched "/queue/event/" anywhere in the destination,
    // so this authorized and registered a destination no broadcast ever targets.
    String destination = "/topic/x/queue/event/" + UUID.randomUUID();
    when(registry.getUserSession(rawSession)).thenReturn(userSession);

    handler.handleBinaryMessage(rawSession, subscribeFrame(destination));

    SocketMessage nack = capturedNack(rawSession);
    assertThat(nack.getHeadersMap()).containsEntry("code", "UNKNOWN_TOPIC");
    verify(liveEventAccessAuthorizer, never())
        .userCanSubscribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(registry, never())
        .subscribe(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void nonCanonicalLiveEventIdIsRejectedAsInvalidTopic() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            mock(SessionExpiryKiller.class),
            meterRegistry,
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-non-canonical", userId);
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    // UUID.fromString accepts this spelling, but broadcasts always use the canonical lowercase
    // form, so the literal-string destination would never receive anything.
    String destination =
        "/topic/queue/event/" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    when(registry.getUserSession(rawSession)).thenReturn(userSession);

    handler.handleBinaryMessage(rawSession, subscribeFrame(destination));

    SocketMessage nack = capturedNack(rawSession);
    assertThat(nack.getHeadersMap()).containsEntry("code", "INVALID_TOPIC");
    verify(liveEventAccessAuthorizer, never())
        .userCanSubscribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(registry, never())
        .subscribe(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    assertThat(
            meterRegistry.counter("opencop.websocket.rejections", "code", "INVALID_TOPIC").count())
        .isEqualTo(1.0);
  }

  @Test
  void userQueueSubscribeIsAcceptedWithoutTopicAuthorization() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    LiveEventAccessAuthorizer liveEventAccessAuthorizer = mock(LiveEventAccessAuthorizer.class);
    WebSocketEventListener listener = mock(WebSocketEventListener.class);
    OCMPWebSocketHandler handler =
        new OCMPWebSocketHandler(
            registry,
            liveEventAccessAuthorizer,
            mock(WatchboxSubscribeAuthorizer.class),
            listener,
            mock(SessionExpiryKiller.class),
            new SimpleMeterRegistry(),
            new ClientMessageValidator(),
            new ProtocolErrorMessageFactory(),
            CLOCK);

    UUID userId = UUID.randomUUID();
    WebSocketSession rawSession = authenticatedSession("ws-user-queue", userId);
    UserSession userSession = new UserSession(rawSession, protocolViolationTracker());
    String destination = "/user/queue/messages";
    when(registry.getUserSession(rawSession)).thenReturn(userSession);

    handler.handleBinaryMessage(rawSession, subscribeFrame(destination));

    verify(listener).onSubscribe(destination, userId);
    verify(rawSession, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    verify(registry, never())
        .subscribe(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  private static BinaryMessage subscribeFrame(String destination) {
    return new BinaryMessage(
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SUBSCRIBE)
            .setDestination(destination)
            .build()
            .toByteArray());
  }

  private static SocketMessage capturedNack(WebSocketSession session) throws Exception {
    var captor = org.mockito.ArgumentCaptor.forClass(BinaryMessage.class);
    verify(session).sendMessage(captor.capture());
    return SocketMessage.parseFrom(captor.getValue().getPayload());
  }

  private static WebSocketSession authenticatedSession(String sessionId, UUID userId) {
    WebSocketSession session = mock(WebSocketSession.class);
    Principal principal = mock(Principal.class);
    when(session.getId()).thenReturn(sessionId);
    when(session.getPrincipal()).thenReturn(principal);
    when(session.getAttributes())
        .thenReturn(
            Map.of(
                ATTR_AUTH,
                authToken(userId),
                ATTR_BACKING_SESSION_ID,
                "bff-" + sessionId,
                ATTR_EXP,
                AUTHENTICATION_DEADLINE,
                ATTR_SESSIONLESS,
                false));
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private static OAuth2AuthenticationToken authToken(UUID userId) {
    OpenCopPrincipal principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId);

    return new OAuth2AuthenticationToken(principal, List.of(), "test-client");
  }

  private static WsSessionRegistry activeRegistry() {
    @SuppressWarnings("unchecked")
    SessionRepository<Session> repository = mock(SessionRepository.class);
    when(repository.findById(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(mock(Session.class));
    return registry(repository);
  }

  private static WsSessionRegistry registry(SessionRepository<? extends Session> repository) {
    return new WsSessionRegistry(repository, userSessionFactory());
  }
}
