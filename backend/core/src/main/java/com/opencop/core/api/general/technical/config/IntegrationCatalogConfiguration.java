// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.factory.IntegrationCatalogRegistrationFactory;
import com.opencop.core.api.general.business.usecase.integrationcatalog.SynchronizeIntegrationCatalogUsecase;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.technical.startup.IntegrationCatalogSynchronizer;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationCatalogConfiguration {

  @Bean
  IntegrationCatalogRegistrationFactory integrationCatalogRegistrationFactory() {
    return new IntegrationCatalogRegistrationFactory();
  }

  @Bean
  SynchronizeIntegrationCatalogUsecase synchronizeIntegrationCatalogUsecase(
      List<Integration> integrations,
      List<IntegrationConnectionSchema> schemas,
      IntegrationCatalogGateway gateway,
      IntegrationCatalogRegistrationFactory factory) {
    return new SynchronizeIntegrationCatalogUsecase(integrations, schemas, gateway, factory);
  }

  @Bean
  IntegrationCatalogSynchronizer integrationCatalogSynchronizer(
      SynchronizeIntegrationCatalogUsecase usecase) {
    return new IntegrationCatalogSynchronizer(usecase);
  }
}
