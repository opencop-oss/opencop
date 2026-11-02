// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.me;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.component.support.TrackingDataSourceRepository;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.layer.ArchiveLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CloneLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CreateLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerMembersUsecase;
import com.opencop.core.api.general.business.usecase.layer.MergeLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.PatchLayerUsecase;
import com.opencop.core.api.general.business.usecase.me.permissions.RetrieveUserDataSourceGroupsUsecase;
import com.opencop.core.api.general.business.usecase.me.permissions.RetrieveUserLayersUsecase;
import com.opencop.core.api.general.business.usecase.me.permissions.RetrieveUserSourcesUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupLayerEmbeddingManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.technical.endpoint.layer.LayersEndpoint;
import com.opencop.core.api.general.technical.endpoint.me.PermissionsEndpoint;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.api.general.technical.gateway.db.datasourcegroup.AppDataSourceGroupService;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.layer.LayerMergeOperationEntity;
import com.opencop.core.api.general.technical.gateway.db.layer.LayerMergeOperationJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.layer.PostgresLayerMergeOperationRepository;
import com.opencop.core.api.general.technical.gateway.db.layer.PostgresMergeLayerValidationGateway;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.user.JpaUserPreferenceRepository;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceEntity;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceJpaRepository;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.general.technical.service.LayerGroupAuthorizationService;
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
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.PostgresLayerActivityReader;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMembersService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.ServerLayerAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.api.iam.technical.gateway.db.server.user.IamServerUserRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.ServerUserAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupDataSourceRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamLayerDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.LayerDataSourceGroupEntity;
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
import com.opencop.core.sdk.technical.gateway.iam.EffectiveSourceRoleRepository;
import com.opencop.core.sdk.technical.gateway.iam.SourceAccessService;
import jakarta.persistence.EntityManager;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      LayerEntity.class,
      LayerMemberEntity.class,
      ServerLayerAccessEntity.class,
      ServerUserAccessEntity.class,
      GroupEntity.class,
      GroupMemberEntity.class,
      GroupLayerMemberEntity.class,
      LayerGroupEntity.class,
      LayerGroupMemberEntity.class,
      LayerGroupLayerEntity.class,
      LayerGroupUserGroupEntity.class,
      DataSourceGroupEntity.class,
      DataSourceGroupMemberEntity.class,
      DataSourceGroupUserGroupEntity.class,
      DataSourceGroupDataSourceEntity.class,
      LayerDataSourceGroupEntity.class,
      LayerMergeOperationEntity.class,
      UserPreferenceEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      IamLayerRepository.class,
      IamLayerMembersRepository.class,
      IamServerLayerRepository.class,
      IamServerUserRepository.class,
      IamGroupRepository.class,
      IamGroupMembersRepository.class,
      IamGroupLayerMemberRepository.class,
      IamLayerGroupMembersRepository.class,
      IamLayerGroupLayerRepository.class,
      IamLayerGroupUserGroupRepository.class,
      IamLayerGroupRepository.class,
      IamDataSourceGroupRepository.class,
      IamDataSourceGroupMembersRepository.class,
      IamDataSourceGroupUserGroupRepository.class,
      IamDataSourceGroupDataSourceRepository.class,
      IamLayerDataSourceGroupRepository.class,
      EffectiveSourceRoleRepository.class,
      LayerMergeOperationJpaRepository.class,
      UserPreferenceJpaRepository.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  PermissionsEndpoint.class,
  LayersEndpoint.class,
  ArchiveLayerUsecase.class,
  CreateLayerUsecase.class,
  LayerMembersUsecase.class,
  PatchLayerUsecase.class,
  MergeLayerUsecase.class,
  LayerAuthorizationService.class,
  AppGroupLayerService.class,
  AppLayerGroupLayerService.class,
  LayerGroupAuthorizationService.class,
  PostgresLayerMergeOperationRepository.class,
  PostgresMergeLayerValidationGateway.class,
  DataSourceRuntimeService.class,
  JdbcDataSourceRepository.class,
  JpaUserPreferenceRepository.class
})
public class PermissionsWebTestApplication {

  // Mocked: these tests assert permissions. Audit rows are proved elsewhere.
  @Bean
  AuditRecorder auditRecorder() {
    return org.mockito.Mockito.mock(AuditRecorder.class);
  }

  @Bean
  LayerAuditProjection layerAuditProjection(ObjectMapper objectMapper) {
    return new LayerAuditProjection();
  }

  @Bean
  @Primary
  TrackingDataSourceRepository trackingDataSourceRepository(JdbcDataSourceRepository delegate) {
    return new TrackingDataSourceRepository(delegate);
  }

  @Bean
  CurrentBrokerProvider currentBrokerProvider(ProviderConfig providerConfig) {
    return new CurrentBrokerProvider(providerConfig.name());
  }

  @Bean
  KafkaProvisioningService kafkaProvisioningService() {
    return Mockito.mock(KafkaProvisioningService.class);
  }

  @Bean
  CloneLayerUsecase cloneLayerUsecase(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuthorizationService,
      PlatformTransactionManager transactionManager,
      JobPublisher jobPublisher,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection) {
    return new CloneLayerUsecase(
        layersRepository,
        layerMemberRepository,
        layerManager,
        layerAuthorizationService,
        transactionManager,
        jobPublisher,
        auditRecorder,
        projection);
  }

  @Bean
  JobPublisher jobPublisher() {
    return Mockito.mock(JobPublisher.class);
  }

  @Bean
  MapCaptureJpaRepository mapCaptureJpaRepository() {
    return Mockito.mock(MapCaptureJpaRepository.class);
  }

  @Bean
  MapTileService mapTileService() {
    return Mockito.mock(MapTileService.class);
  }

  @Bean
  EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway() {
    return Mockito.mock(EventGeoCacheEvictionGateway.class);
  }

  @Bean
  InternalUserRepository internalUserRepository() {
    return Mockito.mock(InternalUserRepository.class);
  }

  @Bean
  LayersRepository layersRepository(
      IamLayerRepository repository, EntityManager entityManager, JdbcTemplate jdbcTemplate) {
    return new LayersService(
        repository,
        new PostgresLayerActivityReader(new NamedParameterJdbcTemplate(jdbcTemplate)),
        entityManager);
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
  ServerUserRepository serverUserRepository(
      IamServerUserRepository repository, UserService userService) {
    return new ServerUserAccessService(repository, userService);
  }

  @Bean
  DataSourceGroupService dataSourceGroupService(
      IamDataSourceGroupRepository dataSourceGroupRepository,
      IamDataSourceGroupMembersRepository membersRepository,
      InternalUserRepository userRepository,
      IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepository,
      IamGroupMembersRepository groupMembersRepository,
      IamDataSourceGroupDataSourceRepository dataSourceRepository,
      DataSourceRepository coreDataSourceRepository) {
    return new AppDataSourceGroupService(
        dataSourceGroupRepository,
        membersRepository,
        userRepository,
        dataSourceGroupUserGroupRepository,
        groupMembersRepository,
        dataSourceRepository,
        coreDataSourceRepository);
  }

  @Bean
  DataSourceGroupLayerEmbeddingManagement dataSourceGroupLayerEmbeddingManagement() {
    return Mockito.mock(DataSourceGroupLayerEmbeddingManagement.class);
  }

  @Bean
  LayerGroupMemberManagement layerGroupMemberManagement() {
    return Mockito.mock(LayerGroupMemberManagement.class);
  }

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
      ServerUserRepository serverUserRepository,
      EffectiveLayerAccessService effectiveLayerAccessService,
      SourceAccessRepository sourceAccessRepository) {
    return UserSpecificAccessFactory.createUserSpecificManager(
        serverLayerRepository,
        serverUserRepository,
        effectiveLayerAccessService,
        sourceAccessRepository);
  }

  @Bean
  SourceAccessRepository sourceAccessRepository(
      EffectiveSourceRoleRepository sourceRoleRepository) {
    return new SourceAccessService(sourceRoleRepository);
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
  RetrieveUserLayersUsecase retrieveUserLayers(
      UserSpecificManager userSpecificManager, LayersRepository layersRepository) {
    return new RetrieveUserLayersUsecase(userSpecificManager, layersRepository);
  }

  @Bean
  RetrieveUserSourcesUsecase retrieveUserSources(
      UserSpecificManager userSpecificManager, DataSourceRuntimeService runtimeService) {
    return new RetrieveUserSourcesUsecase(userSpecificManager, runtimeService);
  }

  @Bean
  RetrieveUserDataSourceGroupsUsecase retrieveUserDataSourceGroups(
      DataSourceGroupService dataSourceGroupService) {
    return new RetrieveUserDataSourceGroupsUsecase(dataSourceGroupService);
  }

  @Bean
  ProviderConfig providerConfig() {
    return new LocalProviderProperties("localhost:9092", "localhost:9092", 1_048_576L, 1);
  }
}
