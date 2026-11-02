// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config;

import org.springframework.boot.SpringApplication;

/** Assembles production Spring applications with OpenCOP's shared bootstrap naming policy. */
public final class SpringApplicationFactory {

  private SpringApplicationFactory() {}

  public static SpringApplication create(Class<?>... primarySources) {
    var application = new SpringApplication(primarySources);
    application.setBeanNameGenerator(FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE);
    return application;
  }
}
