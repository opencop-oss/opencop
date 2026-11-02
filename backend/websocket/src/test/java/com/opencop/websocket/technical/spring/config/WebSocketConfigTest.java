// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.websocket.technical.spring.websocket.OCMPWebSocketHandler;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import com.opencop.websocket.technical.spring.websocket.UserSession;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

class WebSocketConfigTest {

  private final ApplicationContextRunner protocolPropertiesContext =
      new ApplicationContextRunner().withUserConfiguration(ProtocolPropertiesTestConfig.class);

  @Test
  void protocolPropertiesDefaultToExactlySixtyFourKilobytes() {
    protocolPropertiesContext.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(WebSocketProtocolProperties.class).maxInboundMessageBytes())
              .isEqualTo(65_536);
          assertThat(context.getBean(WebSocketProtocolProperties.class).getProtocolViolationLimit())
              .isEqualTo(10);
          assertThat(
                  context.getBean(WebSocketProtocolProperties.class).getProtocolViolationWindow())
              .isEqualTo(java.time.Duration.ofSeconds(60));
        });
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 10_000})
  void protocolViolationLimitAcceptsInclusiveBounds(int configuredLimit) {
    protocolPropertiesContext
        .withPropertyValues("websocket.protocol-violation-limit=" + configuredLimit)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(
                      context
                          .getBean(WebSocketProtocolProperties.class)
                          .getProtocolViolationLimit())
                  .isEqualTo(configuredLimit);
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 10_001})
  void protocolViolationLimitRejectsValuesOutsideInclusiveBounds(int configuredLimit) {
    protocolPropertiesContext
        .withPropertyValues("websocket.protocol-violation-limit=" + configuredLimit)
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1s", "1h"})
  void protocolViolationWindowAcceptsInclusiveExplicitUnitBounds(String configuredWindow) {
    protocolPropertiesContext
        .withPropertyValues("websocket.protocol-violation-window=" + configuredWindow)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(
                      context
                          .getBean(WebSocketProtocolProperties.class)
                          .getProtocolViolationWindow())
                  .isEqualTo(
                      configuredWindow.equals("1s")
                          ? java.time.Duration.ofSeconds(1)
                          : java.time.Duration.ofHours(1));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"1000", "+1000", "0s", "-1s", "999ms", "3600001ms", "2h"})
  void protocolViolationWindowRejectsBareOrOutOfRangeValues(String configuredWindow) {
    protocolPropertiesContext
        .withPropertyValues("websocket.protocol-violation-window=" + configuredWindow)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void applicationEnvironmentVariablesOverrideAllCheckedInProtocolDefaults() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(ProtocolPropertiesTestConfig.class)
        .withSystemProperties(
            "WEBSOCKET_MAX_INBOUND_MESSAGE_SIZE=128KB",
            "WEBSOCKET_PROTOCOL_VIOLATION_LIMIT=7",
            "WEBSOCKET_PROTOCOL_VIOLATION_WINDOW=2m")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var properties = context.getBean(WebSocketProtocolProperties.class);
              assertThat(properties.maxInboundMessageBytes()).isEqualTo(131_072);
              assertThat(properties.getProtocolViolationLimit()).isEqualTo(7);
              assertThat(properties.getProtocolViolationWindow())
                  .isEqualTo(java.time.Duration.ofMinutes(2));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"1KB", "2147483647B"})
  void protocolPropertiesAcceptTheInclusiveContainerByteRange(String configuredSize) {
    protocolPropertiesContext
        .withPropertyValues("websocket.max-inbound-message-size=" + configuredSize)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(
                      context.getBean(WebSocketProtocolProperties.class).maxInboundMessageBytes())
                  .isEqualTo(configuredSize.equals("1KB") ? 1_024 : Integer.MAX_VALUE);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "1024", "0B", "-1KB", "1023B", "2147483648B"})
  void protocolPropertiesRejectInvalidOrOutOfRangeValues(String configuredSize) {
    protocolPropertiesContext
        .withPropertyValues("websocket.max-inbound-message-size=" + configuredSize)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void protocolPropertiesCannotBeMutatedAfterStartup() {
    protocolPropertiesContext.run(
        context -> {
          var properties = context.getBean(WebSocketProtocolProperties.class);

          assertThatThrownBy(() -> properties.setMaxInboundMessageSize("128KB"))
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("WebSocket protocol properties are immutable after startup");
          assertThatThrownBy(() -> properties.setProtocolViolationLimit(20))
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("WebSocket protocol properties are immutable after startup");
          assertThatThrownBy(() -> properties.setProtocolViolationWindow("2m"))
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("WebSocket protocol properties are immutable after startup");
        });
  }

  @Test
  void webSocketContainerUsesTheConfiguredLimitForCompleteTextAndBinaryMessages() {
    protocolPropertiesContext
        .withPropertyValues("websocket.max-inbound-message-size=128KB")
        .run(
            context -> {
              var properties = context.getBean(WebSocketProtocolProperties.class);

              var container =
                  config(
                          mock(WsSessionRegistry.class),
                          mock(OCMPWebSocketHandler.class),
                          mock(SessionExpiryKiller.class))
                      .createWebSocketContainer(properties);

              assertThat(container.getMaxTextMessageBufferSize()).isEqualTo(131_072);
              assertThat(container.getMaxBinaryMessageBufferSize()).isEqualTo(131_072);
            });
  }

  @Test
  void startupLogsAllEffectiveProtocolSettingsExactlyOnceWithoutConfigurationSourceNames() {
    var properties = new WebSocketProtocolProperties();
    properties.setMaxInboundMessageSize("128KB");
    properties.setProtocolViolationLimit(20);
    properties.setProtocolViolationWindow("2m");
    properties.afterPropertiesSet();
    var appender = new ListAppender<ILoggingEvent>();
    var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WebSocketConfig.class);
    appender.start();
    logger.addAppender(appender);

    try {
      config(
              mock(WsSessionRegistry.class),
              mock(OCMPWebSocketHandler.class),
              mock(SessionExpiryKiller.class),
              properties)
          .logProtocolSettings();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
    assertThat(appender.list.getFirst().getFormattedMessage())
        .isEqualTo(
            "WebSocket protocol settings: maxInboundMessageBytes=131072 protocolViolationLimit=20 protocolViolationWindow=PT2M")
        .doesNotContain("WEBSOCKET_", "session", "user", "secret");
  }

  @Test
  void heartbeatTick_sendLimitFailureCleansUpThatSessionWithSessionNotReliable() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    OCMPWebSocketHandler handler = mock(OCMPWebSocketHandler.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    WebSocketConfig config = config(registry, handler, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-heartbeat-limit");
    when(webSocketSession.isOpen()).thenReturn(true);
    when(registry.getUsersSessions()).thenReturn(List.of(userSession));
    org.mockito.Mockito.doThrow(
            new SessionLimitExceededException("overflow", CloseStatus.SESSION_NOT_RELIABLE))
        .when(webSocketSession)
        .sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));

    config.heartbeatTick();

    verify(webSocketSession).sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));
    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SESSION_NOT_RELIABLE);
    verify(registry, never()).remove(userSession);
  }

  @Test
  void heartbeatTick_ioFailureCleansUpThatSessionWithServerError() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    OCMPWebSocketHandler handler = mock(OCMPWebSocketHandler.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    WebSocketConfig config = config(registry, handler, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-heartbeat-io");
    when(webSocketSession.isOpen()).thenReturn(true);
    when(registry.getUsersSessions()).thenReturn(List.of(userSession));
    org.mockito.Mockito.doThrow(new IOException("ping failed"))
        .when(webSocketSession)
        .sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));

    config.heartbeatTick();

    verify(webSocketSession).sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));
    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SERVER_ERROR);
    verify(registry, never()).remove(userSession);
  }

  @Test
  void stopHeartbeatCompletesWhileSendIsBlockedAndPreventsLaterSends() throws Exception {
    var registry = mock(WsSessionRegistry.class);
    var config =
        config(registry, mock(OCMPWebSocketHandler.class), mock(SessionExpiryKiller.class));
    var blockingSession = mock(UserSession.class);
    var laterSession = mock(UserSession.class);
    var blockingSocket = mock(WebSocketSession.class);
    var laterSocket = mock(WebSocketSession.class);
    var sendStarted = new CountDownLatch(1);
    var releaseSend = new CountDownLatch(1);
    var stopStarted = new CountDownLatch(1);
    var stopCompleted = new CountDownLatch(1);
    var heartbeatFailure = new AtomicReference<Throwable>();
    when(blockingSession.getWebsocketSession()).thenReturn(blockingSocket);
    when(laterSession.getWebsocketSession()).thenReturn(laterSocket);
    when(blockingSocket.isOpen()).thenReturn(true);
    when(laterSocket.isOpen()).thenReturn(true);
    when(registry.getUsersSessions()).thenReturn(List.of(blockingSession, laterSession));
    doAnswer(
            _ -> {
              sendStarted.countDown();
              releaseSend.await(5, TimeUnit.SECONDS);
              return null;
            })
        .when(blockingSocket)
        .sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));
    var heartbeatThread =
        Thread.ofPlatform()
            .name("blocked-heartbeat-test")
            .start(
                () -> {
                  try {
                    config.heartbeatTick();
                  } catch (Throwable failure) {
                    heartbeatFailure.set(failure);
                  }
                });

    assertThat(sendStarted.await(2, TimeUnit.SECONDS)).isTrue();
    var stopThread =
        Thread.ofPlatform()
            .name("heartbeat-stop-test")
            .start(
                () -> {
                  stopStarted.countDown();
                  config.stopHeartbeat();
                  stopCompleted.countDown();
                });
    assertThat(stopStarted.await(2, TimeUnit.SECONDS)).isTrue();

    try {
      assertThat(stopCompleted.await(1, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseSend.countDown();
      heartbeatThread.join(TimeUnit.SECONDS.toMillis(2));
      stopThread.join(TimeUnit.SECONDS.toMillis(2));
    }

    assertThat(heartbeatThread.isAlive()).isFalse();
    assertThat(stopThread.isAlive()).isFalse();
    assertThat(heartbeatFailure.get()).isNull();
    verify(laterSocket, never()).sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));
  }

  @Test
  void cleanupHeartbeatSession_delegatesToTheCentralClosePath() {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    OCMPWebSocketHandler handler = mock(OCMPWebSocketHandler.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    WebSocketConfig config = config(registry, handler, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-heartbeat-cleanup");
    when(webSocketSession.isOpen()).thenReturn(true);

    config.cleanupHeartbeatSession(userSession, CloseStatus.SERVER_ERROR);

    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SERVER_ERROR);
    verify(registry, never()).remove(userSession);
  }

  @Test
  void cleanupHeartbeatSession_skipsCloseForClosedWebSocket() throws Exception {
    WsSessionRegistry registry = mock(WsSessionRegistry.class);
    OCMPWebSocketHandler handler = mock(OCMPWebSocketHandler.class);
    SessionExpiryKiller sessionExpiryKiller = mock(SessionExpiryKiller.class);
    WebSocketConfig config = config(registry, handler, sessionExpiryKiller);

    UserSession userSession = mock(UserSession.class);
    WebSocketSession webSocketSession = mock(WebSocketSession.class);
    when(userSession.getWebsocketSession()).thenReturn(webSocketSession);
    when(webSocketSession.getId()).thenReturn("ws-heartbeat-closed");
    when(webSocketSession.isOpen()).thenReturn(false);

    config.cleanupHeartbeatSession(userSession, CloseStatus.SESSION_NOT_RELIABLE);

    verify(sessionExpiryKiller).closeAndCleanup(userSession, CloseStatus.SESSION_NOT_RELIABLE);
    verify(registry, never()).remove(userSession);
  }

  @Test
  @SuppressWarnings("unchecked")
  void applicationContextShutdownStopsHeartbeatAndItsSchedulerThread() throws Exception {
    Bean bean =
        WebSocketSchedulers.class
            .getDeclaredMethod("wsHeartbeatScheduler")
            .getAnnotation(Bean.class);
    var scheduler = new WebSocketSchedulers().wsHeartbeatScheduler();
    var registry = mock(WsSessionRegistry.class);
    var heartbeatThread = new AtomicReference<Thread>();
    var heartbeatCompleted = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              heartbeatThread.compareAndSet(null, Thread.currentThread());
              heartbeatCompleted.countDown();
              return List.of();
            })
        .when(registry)
        .getUsersSessions();
    var config =
        new WebSocketConfig(
            registry,
            mock(OCMPWebSocketHandler.class),
            mock(SessionExpiryKiller.class),
            mock(SessionLifetimePolicy.class),
            Clock.systemUTC(),
            scheduler,
            mock(CurrentAccountAccessGateway.class),
            new CurrentAccountAccessPolicy(),
            defaultProtocolProperties());
    var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("opencop.dev.mode", "false")));
    context.registerBean("wsHeartbeatScheduler", ThreadPoolTaskScheduler.class, () -> scheduler);
    context.registerBean("webSocketConfig", Object.class, () -> config);

    assertThat(bean).isNotNull();
    try {
      context.refresh();
      assertThat(heartbeatCompleted.await(2, TimeUnit.SECONDS)).isTrue();
    } finally {
      context.close();
    }

    Thread thread = heartbeatThread.get();
    assertThat(thread).isNotNull();
    thread.join(TimeUnit.SECONDS.toMillis(2));
    assertThat(thread.isAlive()).isFalse();
    assertThat(scheduler.getScheduledThreadPoolExecutor().isTerminated()).isTrue();

    clearInvocations(registry);
    config.heartbeatTick();

    verifyNoInteractions(registry);
  }

  @SuppressWarnings("unchecked")
  private static WebSocketConfig config(
      WsSessionRegistry registry,
      OCMPWebSocketHandler handler,
      SessionExpiryKiller sessionExpiryKiller) {
    return config(registry, handler, sessionExpiryKiller, defaultProtocolProperties());
  }

  @SuppressWarnings("unchecked")
  private static WebSocketConfig config(
      WsSessionRegistry registry,
      OCMPWebSocketHandler handler,
      SessionExpiryKiller sessionExpiryKiller,
      WebSocketProtocolProperties protocolProperties) {
    return new WebSocketConfig(
        registry,
        handler,
        sessionExpiryKiller,
        mock(SessionLifetimePolicy.class),
        Clock.systemUTC(),
        mock(TaskScheduler.class),
        mock(CurrentAccountAccessGateway.class),
        new CurrentAccountAccessPolicy(),
        protocolProperties);
  }

  private static WebSocketProtocolProperties defaultProtocolProperties() {
    var properties = new WebSocketProtocolProperties();
    properties.afterPropertiesSet();
    return properties;
  }

  @EnableConfigurationProperties(WebSocketProtocolProperties.class)
  private static class ProtocolPropertiesTestConfig {}
}
