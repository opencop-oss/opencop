// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.integrationcatalog.ManageIntegrationActivationUsecase;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.ManageIntegrationActivation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationActivationConfiguration {

  @Bean
  ManageIntegrationActivation manageIntegrationActivation(
      IntegrationCatalogGateway catalogGateway,
      ConnectionMetadataGateway connectionMetadataGateway) {
    return new ManageIntegrationActivationUsecase(catalogGateway, connectionMetadataGateway);
  }
}
