// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.profile.mapping.PluginMapperCatalogReconciliationUsecase;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import com.opencop.core.api.general.technical.startup.PluginMapperRegistrar;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PluginMapperRegistrationConfiguration {

  @Bean
  PluginMapperCatalogReconciliationUsecase pluginMapperCatalogReconciliationUsecase(
      IngressMapperContributions ingressContributions,
      EgressMapperContributions egressContributions,
      MapperProfileService mappers,
      MapperCatalogGateway catalog) {
    return new PluginMapperCatalogReconciliationUsecase(
        ingressContributions, egressContributions, mappers, catalog);
  }

  @Bean
  PluginMapperRegistrar pluginMapperRegistrar(
      PluginMapperCatalogReconciliationUsecase usecase, StartupLeader startupLeader) {
    return new PluginMapperRegistrar(usecase, startupLeader);
  }
}
