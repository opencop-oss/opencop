// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.HEARTBEAT_EXPIRED;
import static com.opencop.websocket.technical.spring.security.AuthConstants.SESSION_ENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class SessionExpiryKillerTest {

  @Test
  void scheduleClose_heartbeatExpiryUsesHeartbeatReason() throws Exception {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-heartbeat-expiry");
    when(webSocketSession.isOpen()).thenReturn(true);
    when(userSession.beginClosing()).thenReturn(true);

    killer.scheduleClose(
        userSession, Instant.now().minusMillis(1), 0, SessionExpiryKiller.HEARTBEAT);

    verify(webSocketSession).close(new CloseStatus(HEARTBEAT_EXPIRED, "Heartbeat expired"));
  }

  @Test
  void scheduleClose_authExpiryUsesSessionEndedReason() throws Exception {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-auth-expiry");
    when(webSocketSession.isOpen()).thenReturn(true);
    when(userSession.beginClosing()).thenReturn(true);

    killer.scheduleClose(userSession, Instant.now().minusMillis(1), 0, SessionExpiryKiller.AUTH);

    verify(webSocketSession).close(new CloseStatus(SESSION_ENDED, "Session ended"));
  }

  @Test
  void scheduleClose_replacesAndCancelsExistingHeartbeatExpiry() {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> initialFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> replacementFuture = mock(ScheduledFuture.class);
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-stored-heartbeat-task");
    doReturn(initialFuture, replacementFuture)
        .when(heartbeatScheduler)
        .schedule(any(Runnable.class), any(Instant.class));

    killer.scheduleClose(
        userSession, Instant.now().plusSeconds(10), 0, SessionExpiryKiller.HEARTBEAT);
    killer.scheduleClose(
        userSession, Instant.now().plusSeconds(15), 0, SessionExpiryKiller.HEARTBEAT);

    verify(initialFuture).cancel(false);
  }

  @Test
  void scheduleClose_cancelsTheFutureWhenExpiryRunsBeforeSchedulingReturns() {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> immediateFuture = mock(ScheduledFuture.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return immediateFuture;
            })
        .when(authScheduler)
        .schedule(any(Runnable.class), any(Instant.class));
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);
    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-immediate-expiry");
    when(userSession.beginClosing()).thenReturn(true);
    doAnswer(
            _ -> {
              assertFalse(Thread.holdsLock(userSession));
              return null;
            })
        .when(registry)
        .remove(userSession);

    killer.scheduleClose(userSession, Instant.now().plusSeconds(30), 0, SessionExpiryKiller.AUTH);

    verify(immediateFuture).cancel(false);
    verify(registry).remove(userSession);
  }

  @Test
  void closeAndCleanup_closesBeforeCancellingAndRemovingAndIgnoresDuplicateRace() throws Exception {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> authFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
    doReturn(authFuture).when(authScheduler).schedule(any(Runnable.class), any(Instant.class));
    doReturn(heartbeatFuture)
        .when(heartbeatScheduler)
        .schedule(any(Runnable.class), any(Instant.class));
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);
    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(userSession.beginClosing()).thenReturn(true, false);
    when(webSocketSession.getId()).thenReturn("ws-close-once");
    when(webSocketSession.isOpen()).thenReturn(true);
    killer.scheduleClose(userSession, Instant.now().plusSeconds(30), 0, SessionExpiryKiller.AUTH);
    killer.scheduleClose(
        userSession, Instant.now().plusSeconds(30), 0, SessionExpiryKiller.HEARTBEAT);
    var status = new CloseStatus(SESSION_ENDED, "Session ended");

    killer.closeAndCleanup(userSession, status);
    killer.closeAndCleanup(userSession, status);

    var authCalls = inOrder(webSocketSession, authFuture, registry);
    authCalls.verify(webSocketSession).close(status);
    authCalls.verify(authFuture).cancel(false);
    authCalls.verify(registry).remove(userSession);
    var heartbeatCalls = inOrder(webSocketSession, heartbeatFuture, registry);
    heartbeatCalls.verify(webSocketSession).close(status);
    heartbeatCalls.verify(heartbeatFuture).cancel(false);
    heartbeatCalls.verify(registry).remove(userSession);
    verify(webSocketSession, times(1)).close(status);
    verify(userSession, times(1)).clearProtocolState();
    verify(registry, times(1)).remove(userSession);
  }

  @Test
  void closeAndCleanup_delegatesCloseWhenSpringDecoratorCloseLockIsContended() throws Exception {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);
    WebSocketSession rawSession = mock(WebSocketSession.class);
    when(rawSession.getId()).thenReturn("ws-contended-close");
    when(rawSession.isOpen()).thenReturn(true);
    AckingWebSocketSessionDecorator decoratedSession =
        new AckingWebSocketSessionDecorator(
            rawSession, UserSession.SEND_TIME_LIMIT_MS, UserSession.BUFFER_SIZE_LIMIT);
    UserSession userSession = mock(UserSession.class);
    when(userSession.getWebsocketSession()).thenReturn(decoratedSession);
    when(userSession.beginClosing()).thenReturn(true, false);
    var status = new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "Protocol limit exceeded");

    Lock inheritedCloseLock = inheritedCloseLock(decoratedSession);
    AtomicReference<Throwable> closeFailure = new AtomicReference<>();
    Thread closeThread =
        new Thread(
            () -> {
              try {
                killer.closeAndCleanup(userSession, status);
              } catch (Throwable e) {
                closeFailure.set(e);
              }
            },
            "contended-close-test");

    inheritedCloseLock.lock();
    try {
      closeThread.start();
      closeThread.join(1_000);
    } finally {
      inheritedCloseLock.unlock();
    }

    assertThat(closeThread.isAlive()).isFalse();
    assertThat(closeFailure.get()).isNull();
    killer.closeAndCleanup(userSession, status);

    var lifecycleCalls = inOrder(rawSession, userSession, registry);
    lifecycleCalls.verify(rawSession).close(status);
    lifecycleCalls.verify(userSession).clearProtocolState();
    lifecycleCalls.verify(registry).remove(userSession);
    verify(rawSession, times(1)).close(status);
    verify(userSession, times(1)).clearProtocolState();
    verify(registry, times(1)).remove(userSession);
  }

  @Test
  void closeAndCleanup_logsOneSanitizedWarningWhenTransportCloseThrows() throws Exception {
    TaskScheduler authScheduler = mock(TaskScheduler.class);
    TaskScheduler heartbeatScheduler = mock(TaskScheduler.class);
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    SessionExpiryKiller killer =
        new SessionExpiryKiller(authScheduler, heartbeatScheduler, registry);
    WebSocketSession rawSession = mock(WebSocketSession.class);
    when(rawSession.getId()).thenReturn("ws-throwing-close");
    when(rawSession.isOpen()).thenReturn(true);
    var status = new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "sensitive reason");
    doThrow(new IOException("sensitive transport detail")).when(rawSession).close(status);
    AckingWebSocketSessionDecorator decoratedSession =
        new AckingWebSocketSessionDecorator(
            rawSession, UserSession.SEND_TIME_LIMIT_MS, UserSession.BUFFER_SIZE_LIMIT);
    UserSession userSession = mock(UserSession.class);
    when(userSession.getWebsocketSession()).thenReturn(decoratedSession);
    when(userSession.beginClosing()).thenReturn(true, false);
    var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SessionExpiryKiller.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      killer.closeAndCleanup(userSession, status);
      killer.closeAndCleanup(userSession, status);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    var lifecycleCalls = inOrder(rawSession, userSession, registry);
    lifecycleCalls.verify(rawSession).close(status);
    lifecycleCalls.verify(userSession).clearProtocolState();
    lifecycleCalls.verify(registry).remove(userSession);
    verify(rawSession, times(1)).close(status);
    verify(userSession, times(1)).clearProtocolState();
    verify(registry, times(1)).remove(userSession);
    assertThat(appender.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Failed to close websocket session ws-throwing-close with status 1008 (IOException)")
        .allSatisfy(
            message -> {
              assertThat(message).doesNotContain("sensitive reason");
              assertThat(message).doesNotContain("sensitive transport detail");
            });
  }

  private static Lock inheritedCloseLock(AckingWebSocketSessionDecorator session)
      throws ReflectiveOperationException {
    Field closeLock = ConcurrentWebSocketSessionDecorator.class.getDeclaredField("closeLock");
    closeLock.setAccessible(true);
    return (Lock) closeLock.get(session);
  }
}
