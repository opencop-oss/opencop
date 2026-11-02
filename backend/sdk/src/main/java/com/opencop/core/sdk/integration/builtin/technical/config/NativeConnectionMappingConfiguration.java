// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.config;

import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionMappingContribution;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes Native's versioned connection mapping contract. */
@Configuration(proxyBeanMethods = false)
public class NativeConnectionMappingConfiguration {

  @Bean
  ConnectionMappingContribution nativeConnectionMapping(NativeConnectionSchema schema) {
    return new NativeConnectionMappingContribution(schema);
  }
}
