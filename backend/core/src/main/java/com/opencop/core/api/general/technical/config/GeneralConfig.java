// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerDataImporterUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.media.LayerMediaRepository;
import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.layer.LayerDataImporter;
import com.opencop.core.api.general.technical.gateway.cache.RedisEventGeoCacheEvictionGateway;
import com.opencop.core.api.general.technical.gateway.db.connection.PostgresSocketReconciliationNudgePublisher;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.PostgresLayerLastEventWriter;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.dataimport.LayerImportProperties;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthMaintenance;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochWriter;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationMaintenance;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import com.opencop.core.sdk.technical.gateway.api.ConnectServiceRedisAdapter;
import com.opencop.core.sdk.technical.gateway.api.PostgresConnectWorkerHealthRepository;
import com.opencop.core.sdk.technical.gateway.api.PostgresSourceRestartEpochRepository;
import com.opencop.core.sdk.technical.gateway.api.PostgresSourceRuntimeObservationRepository;
import com.opencop.core.sdk.technical.gateway.audit.LastEventAtCoalescer;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import jakarta.persistence.EntityManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(LayerImportProperties.class)
public class GeneralConfig {

  @Bean("connectInstanceResolver")
  ConnectInstanceResolver connectInstanceResolver(NamedParameterJdbcTemplate jdbc) {
    return new PostgresConnectWorkerHealthRepository(jdbc);
  }

  @Bean("connectWorkerHealthMaintenance")
  ConnectWorkerHealthMaintenance connectWorkerHealthMaintenance(NamedParameterJdbcTemplate jdbc) {
    return new PostgresConnectWorkerHealthRepository(jdbc);
  }

  @Bean("sourceRuntimeObservationReader")
  SourceRuntimeObservationReader sourceRuntimeObservationReader(NamedParameterJdbcTemplate jdbc) {
    return new PostgresSourceRuntimeObservationRepository(jdbc);
  }

  @Bean("sourceRuntimeObservationMaintenance")
  SourceRuntimeObservationMaintenance sourceRuntimeObservationMaintenance(
      NamedParameterJdbcTemplate jdbc) {
    return new PostgresSourceRuntimeObservationRepository(jdbc);
  }

  /** Coalesces event-detail opens before they reach the resource-activity projection. */
  @Bean(name = "layerLastEventRecorder", destroyMethod = "close")
  LastEventAtCoalescer layerLastEventRecorder(NamedParameterJdbcTemplate jdbc) {
    var coalescer =
        new LastEventAtCoalescer(
            "layer",
            new PostgresLayerLastEventWriter(jdbc),
            LastEventAtCoalescer.DEFAULT_FLUSH_INTERVAL);
    coalescer.start();
    return coalescer;
  }

  @Bean
  ConnectService connectApi(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    return new ConnectServiceRedisAdapter(redisTemplate, objectMapper);
  }

  @Bean
  EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway(
      StringRedisTemplate redisTemplate,
      EventGeoCacheKeyLayout layout,
      RedisEventGeoSourceDirectory sourceDirectory) {
    return new RedisEventGeoCacheEvictionGateway(redisTemplate, layout, sourceDirectory);
  }

  @Bean
  SocketReconciliationNudgePublisher socketReconciliationNudgePublisher(
      EntityManager entityManager, ObjectMapper objectMapper) {
    return new PostgresSocketReconciliationNudgePublisher(entityManager, objectMapper);
  }

  @Bean
  SourceRestartEpochWriter sourceRestartEpochWriter(NamedParameterJdbcTemplate jdbc) {
    return new PostgresSourceRestartEpochRepository(jdbc);
  }

  @Bean
  LayerAuthorizationService layerAuthorizationService(
      LayerManager<Layer, LayerMember> layerManager) {
    return new LayerAuthorizationService(layerManager);
  }

  @Bean
  public LayerDataImporter layerDataImporter(
      MapperProfileService mapperProfileService,
      AccessRepository<LayerRole> layerAccess,
      MessageBrokerProducer<byte[]> messageBrokerProducer,
      MediaStore mediaStore,
      BlobClient blobClient,
      MediaRepository mediaRepository,
      LayerMediaRepository layerMediaRepository,
      ObjectMapper objectMapper,
      LayerImportProperties layerImportProperties,
      LayerManager<Layer, LayerMember> layerManager,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    return new LayerDataImporterUsecase(
        mapperProfileService,
        layerAccess,
        messageBrokerProducer,
        mediaStore,
        blobClient,
        mediaRepository,
        layerMediaRepository,
        objectMapper,
        layerImportProperties,
        layerManager,
        auditRecorder,
        projection,
        transactionManager);
  }
}
