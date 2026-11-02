// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration.connection.mapping;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConnectionMappingConfiguration {
  @Bean
  ConnectionMappingRegistry connectionMappingRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<ConnectionMappingContribution> contributions) {
    return new ConnectionMappingRegistry(schemas, contributions);
  }
}
