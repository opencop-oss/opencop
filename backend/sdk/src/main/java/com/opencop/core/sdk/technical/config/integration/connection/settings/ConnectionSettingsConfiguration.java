// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration.connection.settings;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.integration.connection.settings.ConnectionSettingsCapability;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Collects connection schemas and connection settings capabilities into the SDK registry. */
@Configuration(proxyBeanMethods = false)
public class ConnectionSettingsConfiguration {

  @Bean
  ConnectionSettingsRegistry connectionSettingsRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<ConnectionSettingsCapability<?>> capabilities) {
    return new ConnectionSettingsRegistry(schemas, capabilities);
  }
}
