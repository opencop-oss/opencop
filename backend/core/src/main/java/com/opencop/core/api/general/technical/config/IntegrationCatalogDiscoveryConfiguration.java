// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.integrationcatalog.DiscoverIntegrationsUsecase;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.DiscoverIntegrations;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationCatalogDiscoveryConfiguration {

  @Bean
  DiscoverIntegrations discoverIntegrations(
      IntegrationCatalogGateway catalogGateway,
      List<IntegrationConnectionSchema> connectionSchemas) {
    return new DiscoverIntegrationsUsecase(catalogGateway, connectionSchemas);
  }
}
