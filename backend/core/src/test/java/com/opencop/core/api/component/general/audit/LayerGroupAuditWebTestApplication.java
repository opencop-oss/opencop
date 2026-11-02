// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupContainerUseCase;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupLayersUseCase;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupMembersUseCase;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupUserGroupsUseCase;
import com.opencop.core.api.general.technical.gateway.db.audit.JpaAuditHistoryStore;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupService;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupUserGroupService;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.service.LayerGroupAuthorizationService;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.LayersService;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMembersService;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryEntity;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryJpaRepository;
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
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      LayerEntity.class,
      LayerMemberEntity.class,
      GroupEntity.class,
      GroupMemberEntity.class,
      GroupLayerMemberEntity.class,
      LayerGroupEntity.class,
      LayerGroupMemberEntity.class,
      LayerGroupLayerEntity.class,
      LayerGroupUserGroupEntity.class,
      InternalUserEntity.class,
      AuditHistoryEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      IamLayerRepository.class,
      IamLayerMembersRepository.class,
      IamGroupRepository.class,
      IamGroupMembersRepository.class,
      IamGroupLayerMemberRepository.class,
      IamLayerGroupRepository.class,
      IamLayerGroupMembersRepository.class,
      IamLayerGroupLayerRepository.class,
      IamLayerGroupUserGroupRepository.class,
      InternalUserRepository.class,
      AuditHistoryJpaRepository.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  LayerGroupContainerUseCase.class,
  LayerGroupLayersUseCase.class,
  LayerGroupMembersUseCase.class,
  LayerGroupUserGroupsUseCase.class,
  AppLayerGroupService.class,
  AppLayerGroupLayerService.class,
  AppLayerGroupUserGroupService.class,
  JpaAuditHistoryStore.class,
  LayerGroupAuthorizationService.class
})
public class LayerGroupAuditWebTestApplication {

  @Bean
  com.opencop.core.api.general.business.usecase.layergroup.LayerGroupAuditProjection
      layerGroupAuditProjection() {
    return new com.opencop.core.api.general.business.usecase.layergroup.LayerGroupAuditProjection();
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
}
