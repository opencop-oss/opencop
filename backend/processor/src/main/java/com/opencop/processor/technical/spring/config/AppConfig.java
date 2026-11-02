// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

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

import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.ports.gateway.db.notification.NotificationService;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.gateway.api.PostgresSourceRuntimeObservationRepository;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisPublisher;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.processor.business.usecase.event.JobHandlerRegistry;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.event.MapRawToGeoHandler;
import com.opencop.processor.business.usecase.event.RealTimeGeoFeatureHandler;
import com.opencop.processor.business.usecase.event.ValidateGeoFeature;
import com.opencop.processor.business.usecase.notification.UserNotificationHandler;
import com.opencop.processor.ports.business.observability.mapping.MappingObservationFailureSink;
import com.opencop.processor.ports.business.observability.mapping.RealtimeMappingOutcomeObserver;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import com.opencop.processor.ports.business.usecase.event.RawEventHandler;
import com.opencop.processor.ports.business.usecase.event.RealTimeEventHandler;
import com.opencop.processor.ports.business.usecase.event.ValidateEvent;
import com.opencop.processor.ports.business.usecase.notification.NotificationHandler;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import java.util.List;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Order(1)
@Configuration
// Processor hosts ingress mapping only and intentionally rejects identity, connection behavior, and
// credential extraction. This policy is exhaustive: every catalog addition must be classified here
// before Processor can start.
@EnableIntegrationCapabilities(
    supported = INGRESS_MAPPING,
    rejected = {
      INTEGRATION_IDENTITY,
      CONNECTION_SCHEMA,
      EGRESS_MAPPING,
      CONNECTION_SETTINGS,
      CONNECTION_BEHAVIOR,
      CONNECTION_INPUT,
      CONNECTION_MAPPING,
      GEOCODING,
      CONNECTION_EXTENSION
    })
@EnableConfigurationProperties(PipelinePublishProperties.class)
@ComponentScan({
  "com/opencop/core/sdk/technical/gateway/mapping",
  "com/opencop/core/sdk/technical/exception",
  "com/opencop/core/sdk/technical/gateway/profile",
  "com/opencop/core/sdk/technical/mapper",
  "com/opencop/core/sdk/technical/gateway/db/notification",
  "com/opencop/core/sdk/technical/gateway/broker",
  "com/opencop/core/sdk/technical/gateway/blob",
  "com/opencop/core/sdk/technical/gateway/iam",
  "com/opencop/core/sdk/technical/gateway/db/media"
})
@EntityScan(basePackages = "com.opencop")
@EnableJpaRepositories(basePackages = "com.opencop")
public class AppConfig {

  @Bean
  NotificationHandler<OpencopNotification> userNotificationHandler(
      final CachePublisher<OpencopNotification> cachePublisher,
      final NotificationService notificationService) {
    return new UserNotificationHandler(cachePublisher, notificationService);
  }

  @Bean
  ValidateEvent<Feature> validateGeoFeature(
      @Qualifier("validatedProducer") final MessageBrokerProducer<Feature> messageBrokerProducer,
      final PipelinePublishProperties publishProperties) {
    return new ValidateGeoFeature(messageBrokerProducer, publishProperties.isKeyed());
  }

  @Bean
  RawEventHandler<byte[]> ingestRawEvent(
      final EventDecoderRegistry registry,
      @Qualifier("mappedProducer") final MessageBrokerProducer<Feature> messageBrokerProducer,
      @Qualifier("rawDltProducer") final MessageBrokerProducer<byte[]> rawDltProducer,
      final ObjectMapper objectMapper,
      final BlobClient blobClient,
      final RealtimeMappingOutcomeObserver mappingObservability,
      final MappingObservationFailureSink observationFailureReporter) {
    return new MapRawToGeoHandler(
        registry,
        messageBrokerProducer,
        objectMapper,
        blobClient,
        rawDltProducer,
        mappingObservability,
        observationFailureReporter);
  }

  @Bean
  RealTimeEventHandler<List<Feature>> handleRealTimeGeoFeature(GeoCacheGateway geoCacheWriter) {
    return new RealTimeGeoFeatureHandler(geoCacheWriter);
  }

  @Bean
  JobHandlerRegistry jobHandlerRegistry(List<JobHandler<? extends JobSubmission>> handlers) {
    return new JobHandlerRegistry(handlers);
  }

  @Bean
  JobStatusNotificationPublisher jobStatusNotificationPublisher(
      @Qualifier("userNotificationProducer") MessageBrokerProducer<byte[]> notificationProducer,
      ObjectMapper objectMapper) {
    return new JobStatusNotificationPublisher(notificationProducer, objectMapper);
  }

  @Bean
  SourceRuntimeObservationWriter sourceRuntimeObservationWriter(NamedParameterJdbcTemplate jdbc) {
    return new PostgresSourceRuntimeObservationRepository(jdbc);
  }

  @Bean
  CachePublisher<CacheEventsNotification> realTimeEventsPublisher(
      ObjectMapper om, StringRedisTemplate template) {
    return new RedisPublisher<>(om, CacheChannel.REAL_TIME_EVENTS, template);
  }

  @Bean
  CachePublisher<OpencopNotification> notificationsPublisher(
      ObjectMapper om, StringRedisTemplate template) {
    return new RedisPublisher<>(om, CacheChannel.OPENCOP_NOTIFICATIONS, template);
  }

  @Bean
  CachePublisher<HistoryEvent> historyEventsPublisher(
      ObjectMapper om, StringRedisTemplate template) {
    return new RedisPublisher<>(om, CacheChannel.HISTORY_EVENTS, template);
  }

  @Bean
  CachePublisher<WatchboxAlertEvent> watchboxAlertsPublisher(
      ObjectMapper om, StringRedisTemplate template) {
    return new RedisPublisher<>(om, CacheChannel.WATCHBOX_ALERTS, template);
  }
}
