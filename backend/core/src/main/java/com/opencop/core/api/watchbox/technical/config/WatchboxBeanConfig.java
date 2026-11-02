// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.config;

import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.watchbox.business.factory.WatchboxConfigFactory;
import com.opencop.core.api.watchbox.business.model.WatchboxSettings;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.business.policy.WatchboxPatchPolicy;
import com.opencop.core.api.watchbox.business.service.WatchboxDetectionLivenessService;
import com.opencop.core.api.watchbox.business.service.WatchboxDevStatusOverrideService;
import com.opencop.core.api.watchbox.business.service.WatchboxResponseAssembler;
import com.opencop.core.api.watchbox.business.service.WatchboxSourceEligibilityService;
import com.opencop.core.api.watchbox.business.service.WatchboxValidationService;
import com.opencop.core.api.watchbox.business.usecase.ManageWatchboxesUsecase;
import com.opencop.core.api.watchbox.business.usecase.ReadWatchboxDetectionsUsecase;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditRecorder;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxDetectionReader;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxMapperIdentity;
import com.opencop.core.api.watchbox.ports.usecase.ManageWatchboxes;
import com.opencop.core.api.watchbox.ports.usecase.ReadWatchboxDetections;
import com.opencop.core.api.watchbox.technical.gateway.db.PostgresWatchboxDetectionReader;
import com.opencop.core.api.watchbox.technical.gateway.iam.PostgresWatchboxLayerAccess;
import com.opencop.core.api.watchbox.technical.gateway.mapping.IngressMapperTraitWatchboxMapperIdentity;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.api.PostgresWatchboxDetectionHeartbeatRepository;
import com.opencop.core.sdk.technical.gateway.iam.LayerAccessService;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@EnableConfigurationProperties(WatchboxProperties.class)
public class WatchboxBeanConfig {
  @Bean
  WatchboxDetectionReader watchboxDetectionReader(NamedParameterJdbcTemplate jdbc) {
    return new PostgresWatchboxDetectionReader(jdbc);
  }

  @Bean
  ReadWatchboxDetections readWatchboxDetections(
      WatchboxConfigRepository repository,
      WatchboxLayerAccessPolicy layerAccessPolicy,
      WatchboxDetectionReader reader,
      EventAuthorizationPolicy eventAuthorizationPolicy,
      PostGisService postGisService) {
    return new ReadWatchboxDetectionsUsecase(
        repository, layerAccessPolicy, reader, eventAuthorizationPolicy, postGisService);
  }

  @Bean
  WatchboxSettings watchboxSettings(WatchboxProperties properties) {
    return new WatchboxSettings(properties.maxPolygonVertices());
  }

  @Bean
  WatchboxLayerAccess watchboxLayerAccess(
      LayersRepository layersRepository, NamedParameterJdbcTemplate jdbc) {
    // LayerAccessService is constructed directly (not injected) so composition is identical in
    // the app and in component-test contexts that do not scan the sdk iam gateway package.
    return new PostgresWatchboxLayerAccess(layersRepository, new LayerAccessService(jdbc));
  }

  @Bean
  WatchboxLayerAccessPolicy watchboxLayerAccessPolicy(WatchboxLayerAccess layerAccess) {
    return new WatchboxLayerAccessPolicy(layerAccess);
  }

  @Bean
  WatchboxConfigFactory watchboxConfigFactory() {
    return new WatchboxConfigFactory();
  }

  @Bean
  WatchboxPatchPolicy watchboxPatchPolicy() {
    return new WatchboxPatchPolicy();
  }

  @Bean
  WatchboxValidationService watchboxValidationService(
      WatchboxSettings settings, WatchboxLayerAccess layerAccess) {
    return new WatchboxValidationService(settings, layerAccess);
  }

  @Bean
  WatchboxMapperIdentity watchboxMapperIdentity(IngressMapperContributions contributions) {
    return new IngressMapperTraitWatchboxMapperIdentity(contributions);
  }

  @Bean
  WatchboxSourceEligibilityService watchboxSourceEligibilityService(
      ServerLayerRepository serverLayerRepository,
      DataSourceRepository dataSourceRepository,
      MapperProfileService mapperProfileService,
      WatchboxMapperIdentity mapperIdentity) {
    return new WatchboxSourceEligibilityService(
        serverLayerRepository, dataSourceRepository, mapperProfileService, mapperIdentity);
  }

  @Bean
  WatchboxResponseAssembler watchboxResponseAssembler(
      WatchboxConfigRepository repository,
      DataSourceRepository dataSourceRepository,
      WatchboxLayerAccessPolicy layerAccessPolicy,
      Optional<WatchboxDevStatusOverrideService> devStatusOverrideService) {
    return new WatchboxResponseAssembler(
        repository, dataSourceRepository, layerAccessPolicy, devStatusOverrideService);
  }

  @Bean
  WatchboxDetectionLivenessService watchboxDetectionLivenessService(
      NamedParameterJdbcTemplate jdbc, WatchboxProperties properties) {
    return new WatchboxDetectionLivenessService(
        new PostgresWatchboxDetectionHeartbeatRepository(jdbc),
        properties.detectionDegradeThreshold());
  }

  @Bean
  ManageWatchboxes manageWatchboxes(
      WatchboxConfigRepository repository,
      WatchboxValidationService validationService,
      WatchboxSourceEligibilityService sourceEligibilityService,
      WatchboxResponseAssembler responseAssembler,
      WatchboxLayerAccessPolicy layerAccessPolicy,
      WatchboxConfigFactory configFactory,
      WatchboxPatchPolicy patchPolicy,
      CheckRuntimeFeatureAvailability runtimeFeatureAvailability,
      WatchboxDetectionLivenessService detectionLiveness,
      WatchboxConfigAuditRecorder auditRecorder,
      Optional<WatchboxDevStatusOverrideService> devStatusOverrideService) {
    return new ManageWatchboxesUsecase(
        repository,
        validationService,
        sourceEligibilityService,
        responseAssembler,
        layerAccessPolicy,
        configFactory,
        patchPolicy,
        runtimeFeatureAvailability,
        detectionLiveness,
        auditRecorder,
        devStatusOverrideService);
  }

  @Bean
  @ConditionalOnProperty(name = "opencop.dev.mode", havingValue = "true")
  WatchboxDevStatusOverrideService watchboxDevStatusOverrideService() {
    return new WatchboxDevStatusOverrideService();
  }
}
