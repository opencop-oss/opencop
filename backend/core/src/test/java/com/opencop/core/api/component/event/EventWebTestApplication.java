// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.business.policy.HistoricalTracksAuthorizationPolicy;
import com.opencop.core.api.event.business.policy.HistoricalTracksPreflightPlanner;
import com.opencop.core.api.event.business.policy.HistoricalTracksRequestPolicy;
import com.opencop.core.api.event.business.usecase.attachment.CreateFileEventAttachmentUsecase;
import com.opencop.core.api.event.business.usecase.attachment.CreateUrlEventAttachmentUsecase;
import com.opencop.core.api.event.business.usecase.attachment.DeleteFileEventAttachmentUsecase;
import com.opencop.core.api.event.business.usecase.attachment.DeleteUrlEventAttachmentUsecase;
import com.opencop.core.api.event.business.usecase.attachment.GetFileEventAttachmentUsecase;
import com.opencop.core.api.event.business.usecase.attachment.GetUrlEventAttachmentUsecase;
import com.opencop.core.api.event.business.usecase.event.HistoricalTracksAckFactory;
import com.opencop.core.api.event.business.usecase.event.HistoricalTracksJobSubmissionFactory;
import com.opencop.core.api.event.business.usecase.event.LayerBroadcastEventHandler;
import com.opencop.core.api.event.business.usecase.event.RemarkUsecase;
import com.opencop.core.api.event.business.usecase.event.RetrieveEventDetailUsecase;
import com.opencop.core.api.event.business.usecase.event.RetrieveEventHistoryUsecase;
import com.opencop.core.api.event.business.usecase.event.RetrieveSourceHistogramUsecase;
import com.opencop.core.api.event.business.usecase.event.SourceBroadcastEventHandler;
import com.opencop.core.api.event.business.usecase.event.SubmitHistoricalTracksUsecase;
import com.opencop.core.api.event.ports.gateway.history.HistoricalTracksAdmissionGateway;
import com.opencop.core.api.event.technical.endpoint.BroadcastEvent;
import com.opencop.core.api.event.technical.endpoint.EventAttachmentCreateRegistry;
import com.opencop.core.api.event.technical.endpoint.EventAttachmentDeleteRegistry;
import com.opencop.core.api.event.technical.endpoint.EventAttachmentEndpoint;
import com.opencop.core.api.event.technical.endpoint.EventAttachmentGetRegistry;
import com.opencop.core.api.event.technical.endpoint.HistoricalTracksEndpoint;
import com.opencop.core.api.event.technical.endpoint.RemarksEndpoint;
import com.opencop.core.api.event.technical.endpoint.RetrieveEventDetails;
import com.opencop.core.api.event.technical.endpoint.RetrieveEventHistory;
import com.opencop.core.api.event.technical.endpoint.RetrieveSourceHistogram;
import com.opencop.core.api.event.technical.gateway.PostGisRepository;
import com.opencop.core.api.event.technical.observability.history.HistoricalSubmissionTracing;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.business.usecase.media.AuthorizeStreamPlaybackUsecase;
import com.opencop.core.api.general.business.usecase.media.StreamPlaybackReadiness;
import com.opencop.core.api.general.business.usecase.media.UrlStreamableAttachmentResolver;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.PrepareAttachmentStream;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.api.general.technical.config.MediaMtxProperties;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationEndpoint;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.api.general.technical.gateway.db.remarks.RemarkEntity;
import com.opencop.core.api.general.technical.gateway.db.remarks.RemarkJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.remarks.RemarkRepository;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.PostgresLayerLastEventWriter;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.core.sdk.ports.usecase.media.RegisterMediaUrl;
import com.opencop.core.sdk.technical.audit.AuthzAuditConfig;
import com.opencop.core.sdk.technical.gateway.audit.LastEventAtCoalescer;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.media.EventAttachmentEntity;
import com.opencop.core.sdk.technical.gateway.db.media.EventAttachmentJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.media.JpaEventAttachmentRepository;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentEntity;
import com.opencop.core.sdk.technical.gateway.db.media.MediaUrlEntity;
import com.opencop.core.sdk.technical.gateway.db.media.MediaUrlJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      EventAttachmentEntity.class,
      MediaAttachmentEntity.class,
      MediaUrlEntity.class,
      RemarkEntity.class,
      InternalUserEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      EventAttachmentJpaRepository.class,
      MediaUrlJpaRepository.class,
      RemarkJpaRepository.class
    })
@Import({
  BroadcastEvent.class,
  EventAttachmentEndpoint.class,
  StreamAuthorizationEndpoint.class,
  JpaEventAttachmentRepository.class,
  HistoricalTracksEndpoint.class,
  RemarksEndpoint.class,
  RetrieveEventDetails.class,
  RetrieveEventHistory.class,
  RetrieveSourceHistogram.class,
  EventAttachmentCreateRegistry.class,
  EventAttachmentGetRegistry.class,
  EventAttachmentDeleteRegistry.class,
  CreateFileEventAttachmentUsecase.class,
  CreateUrlEventAttachmentUsecase.class,
  GetFileEventAttachmentUsecase.class,
  GetUrlEventAttachmentUsecase.class,
  DeleteFileEventAttachmentUsecase.class,
  DeleteUrlEventAttachmentUsecase.class,
  EventAttachmentPolicy.class,
  AuthorizeStreamPlaybackUsecase.class,
  StreamAuthorizationTelemetry.class,
  UrlStreamableAttachmentResolver.class,
  PostGisRepository.class,
  HistoricalTracksAuthorizationPolicy.class,
  HistoricalTracksRequestPolicy.class,
  HistoricalTracksPreflightPlanner.class,
  HistoricalTracksAckFactory.class,
  HistoricalTracksJobSubmissionFactory.class,
  RemarkUsecase.class,
  RemarkRepository.class,
  RetrieveEventDetailUsecase.class,
  RetrieveEventHistoryUsecase.class,
  SubmitHistoricalTracksUsecase.class,
  HistoricalSubmissionTracing.class,
  RetrieveSourceHistogramUsecase.class,
  EventAuthorizationPolicy.class,
  ResourceLifecycleGuard.class,
  CoreComponentWebTestConfig.class,
  CoreComponentTestDatabaseConfig.class,
  AuthzAuditConfig.class
})
public class EventWebTestApplication {

  /**
   * The real coalescer over the real writer, with no background timer at all: tests drive {@code
   * flushAll()} themselves so an assertion never races a scheduled flush.
   */
  @Bean(name = "layerLastEventRecorder", destroyMethod = "close")
  LastEventAtCoalescer layerLastEventRecorder(NamedParameterJdbcTemplate jdbc) {
    return LastEventAtCoalescer.withoutScheduler(
        "layer-test",
        new PostgresLayerLastEventWriter(jdbc),
        LastEventAtCoalescer.DEFAULT_FLUSH_INTERVAL);
  }

  @Bean
  LayerBroadcastEventHandler layerBroadcaster() {
    return mock(LayerBroadcastEventHandler.class);
  }

  @Bean
  SourceBroadcastEventHandler sourceBroadcaster() {
    return mock(SourceBroadcastEventHandler.class);
  }

  @Bean
  UserSpecificManager userSpecificManager() {
    var manager = mock(UserSpecificManager.class);
    when(manager.getUserServers(any())).thenReturn(List.of());
    when(manager.getUserLayers(any())).thenReturn(List.of());
    return manager;
  }

  @Bean
  UploadMedia uploadMedia() {
    return mock(UploadMedia.class);
  }

  @Bean
  RetrieveMedia retrieveMedia() {
    return mock(RetrieveMedia.class);
  }

  @Bean
  DeleteMedia deleteMedia() {
    return mock(DeleteMedia.class);
  }

  @Bean
  PrepareAttachmentStream prepareAttachmentStream() {
    return mock(PrepareAttachmentStream.class);
  }

  @Bean
  MediaMtxProperties mediaMtxProperties() {
    var properties = new MediaMtxProperties();
    properties.setEnabled(true);
    return properties;
  }

  @Bean
  StreamPlaybackReadiness streamPlaybackReadiness(MediaMtxProperties properties) {
    return () -> properties.isEnabled() && hasText(properties.getHls().getPublicBaseUrl());
  }

  @Bean
  RegisterMediaUrl registerMediaUrl() {
    return mock(RegisterMediaUrl.class);
  }

  @Bean
  MediaUrlRepository mediaUrlRepository() {
    return mock(MediaUrlRepository.class);
  }

  @Bean
  AuthorizationService authorizationService() {
    return mock(AuthorizationService.class);
  }

  @Bean
  JobPublisher jobPublisher() {
    return mock(JobPublisher.class);
  }

  @Bean
  HistoricalTracksAdmissionGateway historicalTracksAdmissionGateway() {
    return new HistoricalTracksAdmissionGateway() {
      @Override
      public int requestCancellationForActiveJobs(UUID requestedBy, UUID replacementRequestId) {
        return 0;
      }
    };
  }

  @Bean
  @SuppressWarnings("unchecked")
  AccessRepository<ServerRole> sourceAccessRepository() {
    return mock(AccessRepository.class);
  }

  @Bean
  @SuppressWarnings("unchecked")
  AccessRepository<LayerRole> layerAccessRepository() {
    return mock(AccessRepository.class);
  }

  @Bean
  DataSourceRepository dataSourceRepository() {
    var repository = mock(DataSourceRepository.class);
    when(repository.findById(any())).thenReturn(Optional.empty());
    return repository;
  }

  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  @ConditionalOnMissingBean(Tracer.class)
  Tracer tracer() {
    return GlobalOpenTelemetry.getTracer("opencop-core-component-tests");
  }

  @Bean
  @SuppressWarnings("unchecked")
  LayerManager<Layer, LayerMember> layerManager() {
    return mock(LayerManager.class);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
