// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.websocket.technical.spring.security.interceptor.SessionSecurityContextInterceptor;
import com.opencop.websocket.technical.spring.websocket.InboundMessageLimitWebSocketHandlerDecorator;
import com.opencop.websocket.technical.spring.websocket.OCMPWebSocketHandler;
import com.opencop.websocket.technical.spring.websocket.PrincipalHandshakeHandler;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import com.opencop.websocket.technical.spring.websocket.UserSession;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProtocolProperties.class)
public class WebSocketConfig implements WebSocketConfigurer {

  private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
  private final WsSessionRegistry registry;
  private final OCMPWebSocketHandler handler;
  private final SessionExpiryKiller sessionExpiryKiller;
  private final SessionLifetimePolicy sessionLifetimePolicy;
  private final Clock clock;
  private final TaskScheduler heartbeatScheduler;
  private final CurrentAccountAccessGateway currentAccountGateway;
  private final CurrentAccountAccessPolicy currentAccountAdmissionPolicy;
  private final WebSocketProtocolProperties protocolProperties;
  private final Object heartbeatLifecycleMonitor = new Object();
  private ScheduledFuture<?> heartbeatTask;
  private volatile boolean heartbeatStopped;

  @Value("${websocket.allowed-origins:http://localhost:3000,http://localhost:8086}")
  private String allowedOriginsConfig;

  @Value("${opencop.dev.mode}")
  private boolean devMode;

  public WebSocketConfig(
      WsSessionRegistry registry,
      OCMPWebSocketHandler handler,
      SessionExpiryKiller sessionExpiryKiller,
      SessionLifetimePolicy sessionLifetimePolicy,
      Clock clock,
      @Qualifier("wsHeartbeatScheduler") TaskScheduler heartbeatScheduler,
      CurrentAccountAccessGateway currentAccountGateway,
      CurrentAccountAccessPolicy currentAccountAdmissionPolicy,
      WebSocketProtocolProperties protocolProperties) {
    this.registry = registry;
    this.handler = handler;
    this.sessionExpiryKiller = sessionExpiryKiller;
    this.sessionLifetimePolicy = sessionLifetimePolicy;
    this.clock = clock;
    this.heartbeatScheduler = heartbeatScheduler;
    this.currentAccountGateway = currentAccountGateway;
    this.currentAccountAdmissionPolicy = currentAccountAdmissionPolicy;
    this.protocolProperties = protocolProperties;
  }

  @PostConstruct
  void logProtocolSettings() {
    log.info(
        "WebSocket protocol settings: maxInboundMessageBytes={} protocolViolationLimit={} protocolViolationWindow={}",
        protocolProperties.maxInboundMessageBytes(),
        protocolProperties.getProtocolViolationLimit(),
        protocolProperties.getProtocolViolationWindow());
  }

  @PostConstruct
  public void startHeartbeat() {
    synchronized (heartbeatLifecycleMonitor) {
      if (heartbeatStopped) {
        return;
      }
      heartbeatTask =
          heartbeatScheduler.scheduleAtFixedRate(this::heartbeatTick, Duration.ofSeconds(20));
    }
  }

  @PreDestroy
  public void stopHeartbeat() {
    synchronized (heartbeatLifecycleMonitor) {
      heartbeatStopped = true;
      if (heartbeatTask != null) {
        heartbeatTask.cancel(false);
      }
    }
  }

  void heartbeatTick() {
    if (heartbeatStopped) {
      return;
    }
    for (UserSession session : registry.getUsersSessions()) {
      if (heartbeatStopped) {
        return;
      }
      sendHeartbeat(session);
    }
  }

  void sendHeartbeat(UserSession session) {
    try {
      if (session.getWebsocketSession().isOpen()) {
        session.getWebsocketSession().sendMessage(new PingMessage());
      } else {
        cleanupHeartbeatSession(session, CloseStatus.NORMAL);
      }
    } catch (IOException e) {
      log.warn(
          "Failed to send ping to session {}, removing it",
          session.getWebsocketSession().getId(),
          e);
      cleanupHeartbeatSession(session, CloseStatus.SERVER_ERROR);
    } catch (SessionLimitExceededException e) {
      log.warn(
          "Ping send limits exceeded for session {}, removing it",
          session.getWebsocketSession().getId(),
          e);
      cleanupHeartbeatSession(session, CloseStatus.SESSION_NOT_RELIABLE);
    }
  }

  void cleanupHeartbeatSession(UserSession session, CloseStatus closeStatus) {
    sessionExpiryKiller.closeAndCleanup(session, closeStatus);
  }

  @Bean
  ServletServerContainerFactoryBean createWebSocketContainer(
      WebSocketProtocolProperties properties) {
    int maxInboundMessageBytes = properties.maxInboundMessageBytes();
    var container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(maxInboundMessageBytes);
    container.setMaxBinaryMessageBufferSize(maxInboundMessageBytes);
    return container;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    String[] origins = allowedOriginsConfig.split(",");
    log.info("WebSocket allowed origins: {}", allowedOriginsConfig);
    var inboundBoundedHandler =
        new InboundMessageLimitWebSocketHandlerDecorator(
            handler, protocolProperties.maxInboundMessageBytes());
    registry
        .addHandler(inboundBoundedHandler, "/ws")
        .addInterceptors(
            new SessionSecurityContextInterceptor(
                devMode,
                sessionLifetimePolicy,
                clock,
                currentAccountGateway,
                currentAccountAdmissionPolicy))
        .setHandshakeHandler(new PrincipalHandshakeHandler())
        .setAllowedOrigins(origins);
  }
}
