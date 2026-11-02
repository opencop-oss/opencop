// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Collects integration-owned connection extractor beans into their immutable registry. */
@Configuration(proxyBeanMethods = false)
public class ConnectionExtractorConfiguration {

  @Bean
  ConnectionExtractorRegistry connectionExtractorRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<ConnectionExtractorCapability<?, ?>> extractors) {
    return new ConnectionExtractorRegistry(schemas, extractors);
  }
}
