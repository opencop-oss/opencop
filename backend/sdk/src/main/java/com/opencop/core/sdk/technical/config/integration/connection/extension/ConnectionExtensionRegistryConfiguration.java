// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Collects integration-owned connection extension beans into their immutable registry. */
@Configuration(proxyBeanMethods = false)
public class ConnectionExtensionRegistryConfiguration {

  @Bean
  ConnectionExtensionRegistry connectionExtensionRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<ConnectionExtensionCapability<?>> extensions,
      ConnectionSettingsRegistry settingsRegistry) {
    return new ConnectionExtensionRegistry(schemas, extensions, settingsRegistry);
  }
}
