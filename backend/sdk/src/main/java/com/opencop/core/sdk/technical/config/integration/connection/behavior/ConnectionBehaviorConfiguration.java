// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration.connection.behavior;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.behavior.ConnectionBehaviorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;
import com.opencop.core.sdk.technical.config.integration.connection.settings.ConnectionSettingsConfiguration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Collects connection schemas and focused connection behavior beans into the SDK registry, after
 * checking every behavior reads the settings type its schema resolves to.
 */
@Configuration(proxyBeanMethods = false)
@Import(ConnectionSettingsConfiguration.class)
public class ConnectionBehaviorConfiguration {

  @Bean
  ConnectionBehaviorRegistry connectionBehaviorRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<ConnectionBehaviorContribution<?>> contributions,
      ConnectionSettingsRegistry settingsRegistry) {
    settingsRegistry.requireReadableBy(contributions);
    return new ConnectionBehaviorRegistry(schemas, contributions);
  }
}
