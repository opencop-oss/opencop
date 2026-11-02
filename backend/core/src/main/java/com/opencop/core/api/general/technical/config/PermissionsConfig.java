// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.connection.permissions.AddCreateSourcePermissions;
import com.opencop.core.api.general.business.usecase.connection.permissions.DeleteSourcePermissionsUsecase;
import com.opencop.core.api.general.business.usecase.me.permissions.RetrieveUserDataSourceGroupsUsecase;
import com.opencop.core.api.general.business.usecase.me.permissions.RetrieveUserLayersUsecase;
import com.opencop.core.api.general.business.usecase.me.permissions.RetrieveUserSourcesUsecase;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.connection.permissions.CreateSourcePermissions;
import com.opencop.core.api.general.ports.usecase.connection.permissions.DeleteSourcePermissions;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserDataSourceGroups;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserLayers;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserSources;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionsConfig {

  @Bean
  CreateSourcePermissions createSourcePermissions(final ServerManager<Server> serverManager) {
    return new AddCreateSourcePermissions(serverManager);
  }

  @Bean
  DeleteSourcePermissions deleteSourcePermissions(
      final ServerLayerRepository serverLayerRepository,
      final ServerUserRepository serverUserRepository) {
    return new DeleteSourcePermissionsUsecase(serverLayerRepository, serverUserRepository);
  }

  @Bean
  RetrieveUserLayers userLayersPermissions(
      final UserSpecificManager userSpecificManager, final LayersRepository layersRepository) {
    return new RetrieveUserLayersUsecase(userSpecificManager, layersRepository);
  }

  @Bean
  RetrieveUserSources userSourcesPermissions(
      final UserSpecificManager userSpecificManager,
      final DataSourceRuntimeService runtimeService) {
    return new RetrieveUserSourcesUsecase(userSpecificManager, runtimeService);
  }

  @Bean
  RetrieveUserDataSourceGroups userDataSourceGroupsPermissions(
      final DataSourceGroupService dataSourceGroupService) {
    return new RetrieveUserDataSourceGroupsUsecase(dataSourceGroupService);
  }
}
