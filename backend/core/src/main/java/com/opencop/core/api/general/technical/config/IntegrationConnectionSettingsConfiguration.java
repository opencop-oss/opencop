// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.integrationcatalog.DescribeIntegrationConnectionSettingsUsecase;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.DescribeIntegrationConnectionSettings;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationConnectionSettingsConfiguration {

  @Bean
  DescribeIntegrationConnectionSettings describeIntegrationConnectionSettings(
      IntegrationCatalogGateway catalogGateway, ConnectionSettingsRegistry settingsRegistry) {
    return new DescribeIntegrationConnectionSettingsUsecase(catalogGateway, settingsRegistry);
  }
}
