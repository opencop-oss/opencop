// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.config;

import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Registers the Native integration. */
@Lazy
@Configuration(proxyBeanMethods = false)
public class NativeIntegrationIdentityConfiguration {

  @Bean
  NativeIntegration nativeIntegration() {
    return new NativeIntegration();
  }
}
