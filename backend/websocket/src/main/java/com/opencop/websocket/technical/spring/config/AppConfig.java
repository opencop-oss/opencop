// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SCHEMA;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;

import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerJpaRepository;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalChunkDeliveryStateRepository;
import com.opencop.core.sdk.technical.gateway.iam.LayerAccessService;
import com.opencop.core.sdk.technical.security.session.AuthSessionProperties;
import com.opencop.websocket.business.usecase.event.LiveEventAccessPolicy;
import com.opencop.websocket.business.usecase.event.WebSocketEventHandlerFactory;
import com.opencop.websocket.business.usecase.history.HistoricalChunkPartKeyResolver;
import com.opencop.websocket.business.usecase.history.HistoryChunkDeliveryPrecondition;
import com.opencop.websocket.business.usecase.history.UserHistoryChunkSender;
import com.opencop.websocket.business.usecase.history.UserHistoryEventHandler;
import com.opencop.websocket.business.usecase.notification.UserNotificationHandler;
import com.opencop.websocket.business.usecase.watchbox.WatchboxSubscribeAccessPolicy;
import com.opencop.websocket.ports.business.usecase.event.EventHandler;
import com.opencop.websocket.ports.business.usecase.event.LiveEventAccessAuthorizer;
import com.opencop.websocket.ports.business.usecase.event.UserEventHandler;
import com.opencop.websocket.ports.business.usecase.history.HistoryEventHandler;
import com.opencop.websocket.ports.business.usecase.notification.NotificationHandler;
import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.ports.gateway.history.HistoricalChunkPayloadGateway;
import com.opencop.websocket.ports.gateway.iam.LiveEventResourceAccessGateway;
import com.opencop.websocket.ports.gateway.watchbox.WatchboxAccessGateway;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.gateway.history.RedisHistoricalChunkPayloadGateway;
import com.opencop.websocket.technical.gateway.iam.SdkLiveEventResourceAccessGateway;
import com.opencop.websocket.technical.gateway.watchbox.DatabaseWatchboxAccessGateway;
import com.opencop.websocket.technical.gateway.websocket.WebSocketProducer;
import com.opencop.websocket.technical.spring.websocket.OCMPMessagingTemplate;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import com.opencop.websocket.technical.spring.websocket.UserSessionFactory;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.RedisSessionRepository;
import tools.jackson.databind.ObjectMapper;

@EnableScheduling
@Configuration
@ComponentScan({
  "com/opencop/core/sdk/technical/exception",
  "com/opencop/core/sdk/technical/security/session",
  "com/opencop/core/sdk/technical/gateway/iam",
  "com/opencop/core/sdk/technical/gateway/db/account"
})
@EntityScan(basePackages = "com.opencop")
@EnableJpaRepositories(basePackages = "com.opencop")
// WebSocket hosts no current integration capability, so it explicitly rejects the complete current
// catalog. This policy is exhaustive: every catalog addition must be classified here before
// WebSocket can start.
@EnableIntegrationCapabilities(
    supported = {},
    rejected = {
      INTEGRATION_IDENTITY,
      CONNECTION_SCHEMA,
      INGRESS_MAPPING,
      EGRESS_MAPPING,
      CONNECTION_SETTINGS,
      CONNECTION_BEHAVIOR,
      CONNECTION_INPUT,
      CONNECTION_MAPPING,
      GEOCODING,
      CONNECTION_EXTENSION
    })
public class AppConfig {

  private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

  public AppConfig(@Value("${opencop.dev.mode}") boolean devMode) {
    if (devMode) {
      log.warn("=====================================================");
      log.warn("⚠\uFE0F  OpenCOP is running in DEV MODE");
      log.warn("Authentication checks are bypassed.");
      log.warn("DO NOT USE IN PRODUCTION.");
      log.warn("=====================================================");
    }
  }

  @Bean
  public OCMPMessagingTemplate ocmpTemplate(
      WsSessionRegistry registry, SessionExpiryKiller sessionExpiryKiller) {
    return new OCMPMessagingTemplate(registry, sessionExpiryKiller);
  }

  @Bean
  public WsSessionRegistry registry(
      ObjectProvider<RedisSessionRepository> sessionRepositoryProvider,
      UserSessionFactory userSessionFactory) {
    return new WsSessionRegistry(sessionRepositoryProvider.getIfAvailable(), userSessionFactory);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public SessionLifetimePolicy sessionLifetimePolicy(
      AuthSessionProperties properties, Clock clock) {
    return new SessionLifetimePolicy(properties.getAbsoluteLifetime(), clock);
  }

  @Bean
  public CurrentAccountAccessPolicy currentAccountAdmissionPolicy() {
    return new CurrentAccountAccessPolicy();
  }

  @Bean
  SocketProducer socketProducer(final OCMPMessagingTemplate template) {
    return new WebSocketProducer(template);
  }

  @Bean("opencopUserNotificationHandler")
  public NotificationHandler opencopUserNotificationHandler(
      final SocketProducer webSocketProducer) {
    return new UserNotificationHandler(webSocketProducer);
  }

  @Bean("userHistoryEventHandler")
  public HistoryEventHandler userHistoryEventHandler(
      final SocketProducer webSocketProducer,
      final ObjectMapper objectMapper,
      final UserHistoryChunkSender userHistoryChunkSender,
      HistoryJobDeliveryCoordinator historyJobDeliveryCoordinator,
      RedisHistoricalChunkDeliveryStateRepository redisHistoricalChunkDeliveryStateRepository) {
    return new UserHistoryEventHandler(
        webSocketProducer,
        objectMapper,
        userHistoryChunkSender,
        historyJobDeliveryCoordinator,
        redisHistoricalChunkDeliveryStateRepository);
  }

  @Bean
  HistoricalChunkPayloadGateway historicalChunkPayloadGateway(
      final RedisTemplate<String, byte[]> redisTemplate) {
    return new RedisHistoricalChunkPayloadGateway(redisTemplate);
  }

  @Bean
  HistoricalChunkPartKeyResolver historicalChunkPartKeyResolver() {
    return new HistoricalChunkPartKeyResolver();
  }

  @Bean
  HistoryChunkDeliveryPrecondition historyChunkDeliveryPrecondition() {
    return new HistoryChunkDeliveryPrecondition();
  }

  @Bean
  UserHistoryChunkSender userHistoryChunkSender(
      final SocketProducer webSocketProducer,
      final ObjectMapper objectMapper,
      final HistoricalChunkPayloadGateway historicalChunkPayloadGateway,
      final HistoricalChunkPartKeyResolver historicalChunkPartKeyResolver,
      final HistoryChunkDeliveryPrecondition historyChunkDeliveryPrecondition,
      final MeterRegistry meterRegistry) {
    return UserHistoryChunkSender.create(
        webSocketProducer,
        objectMapper,
        historicalChunkPayloadGateway,
        historicalChunkPartKeyResolver,
        historyChunkDeliveryPrecondition,
        meterRegistry,
        GlobalOpenTelemetry.getTracer(HistoricalTelemetry.HISTORICAL_TRACER_NAME),
        UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
  }

  @Bean("openCopEventHandler")
  EventHandler<List<String>> createOpenCopEventHandler(
      WsSessionRegistry sessionRegistry, GeoEventReader reader, SocketProducer webSocketProducer) {
    return WebSocketEventHandlerFactory.createOpenCopEventHandler(
        sessionRegistry, reader, webSocketProducer);
  }

  @Bean("retrieveAllEventsHandler")
  UserEventHandler<String> createOnConnectEventHandler(
      final GeoEventReader reader,
      final SocketProducer webSocketProducer,
      final ObjectMapper objectMapper) {
    return WebSocketEventHandlerFactory.onConnectEventHandler(
        reader, webSocketProducer, objectMapper);
  }

  @Bean
  LiveEventResourceAccessGateway liveEventResourceAccessGateway(
      LayerAccessService layerAccessService,
      AccessRepository<ServerRole> sourceAccessRepository,
      NamedParameterJdbcTemplate jdbcTemplate) {
    return new SdkLiveEventResourceAccessGateway(
        layerAccessService, sourceAccessRepository, jdbcTemplate);
  }

  @Bean
  LiveEventAccessAuthorizer liveEventAccessAuthorizer(
      LiveEventResourceAccessGateway liveEventResourceAccessGateway) {
    return new LiveEventAccessPolicy(liveEventResourceAccessGateway);
  }

  @Bean
  WatchboxAccessGateway watchboxAccessGateway(
      WatchboxJpaRepository watchboxRepository,
      WatchboxLayerJpaRepository watchboxLayerRepository,
      LayerAccessService layerAccessService,
      CurrentAccountAccessGateway currentAccountAccessGateway) {
    return new DatabaseWatchboxAccessGateway(
        watchboxRepository,
        watchboxLayerRepository,
        layerAccessService,
        currentAccountAccessGateway);
  }

  @Bean
  WatchboxSubscribeAuthorizer watchboxSubscribeAuthorizer(
      WatchboxAccessGateway watchboxAccessGateway) {
    return new WatchboxSubscribeAccessPolicy(watchboxAccessGateway);
  }
}
