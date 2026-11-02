// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static com.opencop.core.api.component.watchbox.WatchboxTestMapperIdentity.MAPPER_ID;
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

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.technical.gateway.PostGisRepository;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.business.usecase.access.layer.LayerManagersFactory;
import com.opencop.core.api.iam.business.usecase.access.me.UserSpecificAccessFactory;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.LayersService;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMembersService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.ServerLayerAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.api.watchbox.technical.config.WatchboxBeanConfig;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxConfigAuditEndpoint;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxDetectionsEndpoint;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxDevStatusOverrideEndpoint;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxExceptionAdvice;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxesEndpoint;
import com.opencop.core.api.watchbox.technical.gateway.db.JpaWatchboxConfigAuditStore;
import com.opencop.core.api.watchbox.technical.gateway.db.JpaWatchboxConfigRepository;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.audit.AuthzAuditConfig;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxConfigAuditEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxConfigAuditJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxLayerJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxSourceStatusJpaRepository;
import com.opencop.core.sdk.technical.gateway.iam.EffectiveSourceRoleRepository;
import com.opencop.core.sdk.technical.gateway.iam.SourceAccessService;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Feature;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableIntegrationCapabilities(
    supported = INGRESS_MAPPING,
    rejected = {
      EGRESS_MAPPING,
      INTEGRATION_IDENTITY,
      CONNECTION_SCHEMA,
      CONNECTION_SETTINGS,
      CONNECTION_BEHAVIOR,
      CONNECTION_INPUT,
      CONNECTION_MAPPING,
      GEOCODING,
      CONNECTION_EXTENSION
    })
@EnableMethodSecurity
@EntityScan(
    basePackageClasses = {
      DataSourceGroupEntity.class,
      WatchboxConfigAuditEntity.class,
      WatchboxEntity.class,
      WatchboxLayerEntity.class,
      WatchboxSourceStatusEntity.class,
      LayerEntity.class,
      LayerMemberEntity.class,
      ServerLayerAccessEntity.class,
      GroupEntity.class,
      GroupMemberEntity.class,
      GroupLayerMemberEntity.class,
      LayerGroupEntity.class,
      LayerGroupMemberEntity.class,
      LayerGroupLayerEntity.class,
      LayerGroupUserGroupEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      EffectiveSourceRoleRepository.class,
      WatchboxConfigAuditJpaRepository.class,
      WatchboxJpaRepository.class,
      WatchboxLayerJpaRepository.class,
      WatchboxSourceStatusJpaRepository.class,
      IamLayerRepository.class,
      IamLayerMembersRepository.class,
      IamServerLayerRepository.class,
      IamGroupRepository.class,
      IamGroupMembersRepository.class,
      IamGroupLayerMemberRepository.class,
      IamLayerGroupMembersRepository.class,
      IamLayerGroupLayerRepository.class,
      IamLayerGroupUserGroupRepository.class,
      IamLayerGroupRepository.class
    })
@Import({
  WatchboxDetectionsEndpoint.class,
  EventAuthorizationPolicy.class,
  PostGisRepository.class,
  ResourceLifecycleGuard.class,
  SourceAccessService.class,
  AuthzAuditConfig.class,
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  JdbcDataSourceRepository.class,
  WatchboxConfigAuditEndpoint.class,
  WatchboxesEndpoint.class,
  WatchboxDevStatusOverrideEndpoint.class,
  WatchboxExceptionAdvice.class,
  WatchboxBeanConfig.class,
  JpaWatchboxConfigAuditStore.class,
  JpaWatchboxConfigRepository.class
})
public class WatchboxWebTestApplication {
  @Bean
  EffectiveLayerAccessService effectiveLayerAccessService(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      IamGroupMembersRepository groupMembersRepository,
      IamGroupLayerMemberRepository groupLayerRepository,
      IamLayerGroupMembersRepository layerGroupMembersRepository,
      IamLayerGroupLayerRepository layerGroupLayerRepository,
      IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      IamLayerGroupRepository iamLayerGroupRepository) {
    return new EffectiveLayerAccessService(
        layersRepository,
        layerMemberRepository,
        groupMembersRepository,
        groupLayerRepository,
        layerGroupMembersRepository,
        layerGroupLayerRepository,
        layerGroupUserGroupRepository,
        iamLayerGroupRepository);
  }

  @Bean
  UserSpecificManager userSpecificManager(
      ServerLayerRepository serverLayerRepository,
      EffectiveLayerAccessService effectiveLayerAccessService,
      SourceAccessRepository sourceAccessRepository) {
    // Only getUserDirectServers uses this collaborator; event source policy never calls it.
    var serverUserRepository = Mockito.mock(ServerUserRepository.class);
    return UserSpecificAccessFactory.createUserSpecificManager(
        serverLayerRepository,
        serverUserRepository,
        effectiveLayerAccessService,
        sourceAccessRepository);
  }

  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  IntegrationIngressMappers watchboxTestMappers() {
    return new IntegrationIngressMappers(
        new TestIntegration(
            UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
            "Test integration",
            "Test integration"),
        List.of(watchboxTestMapper()));
  }

  private static PayloadIngressMapper<Feature> watchboxTestMapper() {
    return new PayloadIngressMapper<>() {
      @Override
      public UUID id() {
        return MAPPER_ID;
      }

      @Override
      public String name() {
        return "watchbox-test-mapper";
      }

      @Override
      public String description() {
        return "Stable mapper fixture for Watchbox component tests";
      }

      @Override
      public int version() {
        return 1;
      }

      @Override
      public ObjectIdStability objectIdStability() {
        return ObjectIdStability.STABLE_PER_ENTITY;
      }

      @Override
      public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
        return List.of();
      }
    };
  }

  @Bean
  LayersRepository layersRepository(IamLayerRepository repository, EntityManager entityManager) {
    return new LayersService(repository, _ -> Map.of(), entityManager);
  }

  @Bean
  LayerMemberRepository layerMemberRepository(
      IamLayerMembersRepository repository, IamLayerRepository layersRepository) {
    return new LayerMembersService(repository, layersRepository);
  }

  @Bean
  ServerLayerRepository serverLayerRepository(
      IamServerLayerRepository repository, IamLayerRepository layersRepository) {
    return new ServerLayerAccessService(repository, layersRepository);
  }

  @Bean
  LayerManager<Layer, LayerMember> layerManager(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      ServerLayerRepository serverLayerRepository,
      UserService userService,
      IamGroupMembersRepository groupMembersRepository,
      IamGroupLayerMemberRepository groupLayerRepository,
      IamLayerGroupMembersRepository layerGroupMembersRepository,
      IamLayerGroupLayerRepository layerGroupLayerRepository,
      IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      IamLayerGroupRepository iamLayerGroupRepository) {
    return LayerManagersFactory.layerAccessManager(
        layersRepository,
        layerMemberRepository,
        serverLayerRepository,
        userService,
        groupMembersRepository,
        groupLayerRepository,
        layerGroupMembersRepository,
        layerGroupLayerRepository,
        layerGroupUserGroupRepository,
        iamLayerGroupRepository);
  }

  @Bean
  UserService userService() {
    return Mockito.mock(UserService.class);
  }

  @Bean
  MapperProfileService mapperProfileService() {
    return Mockito.mock(MapperProfileService.class);
  }

  @Bean
  CheckRuntimeFeatureAvailability checkRuntimeFeatureAvailability() {
    return (feature, callerUserId) -> true;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
