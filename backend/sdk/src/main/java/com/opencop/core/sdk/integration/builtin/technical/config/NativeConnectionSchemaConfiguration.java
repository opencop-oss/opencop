// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.config;

import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Registers the connection contract Native connections bind to. */
@Lazy
@Configuration(proxyBeanMethods = false)
public class NativeConnectionSchemaConfiguration {

  @Bean
  NativeConnectionSchema nativeConnectionSchema(NativeIntegration integration) {
    return new NativeConnectionSchema(integration);
  }
}
