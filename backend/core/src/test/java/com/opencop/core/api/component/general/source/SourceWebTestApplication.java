// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.source;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.component.support.MockAuditRecordingConfig;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.service.DataSourceAutoStartService;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.source.SourceLayerRelationshipUsecase;
import com.opencop.core.api.general.business.usecase.source.SourceUsersUsecase;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.endpoint.source.DataSourceAutoStartEndpoint;
import com.opencop.core.api.general.technical.endpoint.source.SourcesEndpoint;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.api.general.technical.gateway.db.connection.JpaConnectionMetadataGateway;
import com.opencop.core.api.general.technical.gateway.db.connection.PatchableDataSourceEntity;
import com.opencop.core.api.general.technical.service.SourceRelationshipAuthorizationService;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.business.usecase.access.layer.LayerManagersFactory;
import com.opencop.core.api.iam.business.usecase.access.me.UserSpecificAccessFactory;
import com.opencop.core.api.iam.business.usecase.access.server.ServerManagersFactory;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.LayersService;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMembersService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.ServerLayerAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.api.iam.technical.gateway.db.server.user.IamServerUserRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.ServerUserAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
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
import com.opencop.core.sdk.technical.gateway.iam.EffectiveSourceRoleRepository;
import com.opencop.core.sdk.technical.gateway.iam.SourceAccessService;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(CoreProperties.class)
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
      // The source-layer use case takes its row lock through JpaConnectionMetadataGateway,
      // which loads this entity.
      PatchableDataSourceEntity.class
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
      EffectiveSourceRoleRepository.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  MockAuditRecordingConfig.class,
  JpaConnectionMetadataGateway.class,
  CoreComponentWebTestConfig.class,
  ConnectRoutingTargetResolver.class,
  SourcesEndpoint.class,
  SourceLayerRelationshipUsecase.class,
  SourceUsersUsecase.class,
  SourceRelationshipAuthorizationService.class,
  DataSourceAutoStartEndpoint.class,
  DataSourceRuntimeService.class,
  DataSourceAutoStartService.class,
  JdbcDataSourceRepository.class
})
public class SourceWebTestApplication {

  @Bean
  CurrentBrokerProvider currentBrokerProvider(ProviderConfig providerConfig) {
    return new CurrentBrokerProvider(providerConfig.name());
  }

  @Bean
  KafkaProvisioningService kafkaProvisioningService() {
    return org.mockito.Mockito.mock(KafkaProvisioningService.class);
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
  ServerUserRepository serverUserRepository(
      IamServerUserRepository repository, UserService userService) {
    return new ServerUserAccessService(repository, userService);
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
  ServerManager<Server> serverManager(
      DataSourceRepository dataSourceRepository,
      ServerLayerRepository serverLayerRepository,
      ServerUserRepository serverUserRepository,
      UserService userService) {
    return ServerManagersFactory.serverAccessManager(
        dataSourceRepository, serverLayerRepository, serverUserRepository, userService);
  }

  @Bean
  ProviderConfig providerConfig() {
    return new LocalProviderProperties("localhost:9092", "localhost:9092", 1_048_576L, 1);
  }
}
