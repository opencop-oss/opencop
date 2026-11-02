// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.business.usecase.access.layer.LayerManagersFactory;
import com.opencop.core.api.iam.business.usecase.access.me.UserSpecificAccessFactory;
import com.opencop.core.api.iam.business.usecase.access.server.ServerManagersFactory;
import com.opencop.core.api.iam.business.usecase.identity.UserManagersFactory;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.LayersService;
import com.opencop.core.api.iam.technical.gateway.db.layers.layer.PostgresLayerActivityReader;
import com.opencop.core.api.iam.technical.gateway.db.layers.member.LayerMembersService;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.ServerLayerAccessService;
import com.opencop.core.api.iam.technical.gateway.db.server.user.IamServerUserRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.ServerUserAccessService;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class IamConfig {

  // ******** User **********
  @Bean
  public UserManager<InternalUser> userManager(
      final UserService userService, final AccountMutationUsecase accountMutationUsecase) {
    return UserManagersFactory.userManager(userService, accountMutationUsecase);
  }

  @Bean
  public EffectiveLayerAccessService effectiveLayerAccessService(
      final LayersRepository layersRepository,
      final LayerMemberRepository layerMemberRepository,
      final IamGroupMembersRepository groupMembersRepository,
      final IamGroupLayerMemberRepository groupLayerRepository,
      final IamLayerGroupMembersRepository layerGroupMembersRepository,
      final IamLayerGroupLayerRepository layerGroupLayerRepository,
      final IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      final IamLayerGroupRepository iamLayerGroupRepository) {
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
  public UserSpecificManager userSpecificManager(
      final ServerLayerRepository serverLayerRepository,
      final ServerUserRepository userRepository,
      final EffectiveLayerAccessService effectiveLayerAccessService,
      final SourceAccessRepository sourceAccessRepository) {
    return UserSpecificAccessFactory.createUserSpecificManager(
        serverLayerRepository, userRepository, effectiveLayerAccessService, sourceAccessRepository);
  }

  // ******** Layers **********
  @Bean
  public LayerManager<Layer, LayerMember> layerAccessManager(
      final LayersRepository repository,
      final LayerMemberRepository layerMemberRepository,
      final ServerLayerRepository serverLayerRepository,
      final UserService userService,
      final IamGroupMembersRepository groupMembersRepository,
      final IamGroupLayerMemberRepository groupLayerRepository,
      final IamLayerGroupMembersRepository layerGroupMembersRepository,
      final IamLayerGroupLayerRepository layerGroupLayerRepository,
      final IamLayerGroupUserGroupRepository layerGroupUserGroupRepository,
      final IamLayerGroupRepository iamLayerGroupRepository) {
    return LayerManagersFactory.layerAccessManager(
        repository,
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
  public LayersRepository createLayersRepository(
      final IamLayerRepository repository,
      final NamedParameterJdbcTemplate jdbc,
      final EntityManager entityManager) {
    return new LayersService(repository, new PostgresLayerActivityReader(jdbc), entityManager);
  }

  @Bean
  public LayerMemberRepository createLayerMembersRepository(
      final IamLayerMembersRepository repository, final IamLayerRepository layersRepository) {
    return new LayerMembersService(repository, layersRepository);
  }

  // ******** Servers **********
  @Bean
  public ServerManager<Server> serverAccessManager(
      final DataSourceRepository dataSourceRepository,
      final ServerLayerRepository layerRepository,
      final ServerUserRepository userRepository,
      final UserService userService) {
    return ServerManagersFactory.serverAccessManager(
        dataSourceRepository, layerRepository, userRepository, userService);
  }

  @Bean
  public ServerLayerRepository createServerLayersRepository(
      final IamServerLayerRepository repository, final IamLayerRepository layersRepository) {
    return new ServerLayerAccessService(repository, layersRepository);
  }

  @Bean
  public ServerUserRepository createServerUsersRepository(
      final IamServerUserRepository repository, final UserService userService) {
    return new ServerUserAccessService(repository, userService);
  }
}
