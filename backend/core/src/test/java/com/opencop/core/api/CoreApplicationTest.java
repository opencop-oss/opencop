// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api;

import static com.opencop.core.sdk.testing.config.SpringApplicationBootstrapAssertions.assertConfiguredApplication;

import com.opencop.core.api.common.AppConfig;
import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import org.junit.jupiter.api.Test;

class CoreApplicationTest {

  @Test
  void configuresTheProductionApplicationBootstrap() {
    assertConfiguredApplication(
        CoreApplication.createApplication(),
        FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE,
        CoreApplication.class,
        AppConfig.class);
  }

  @org.junit.jupiter.api.Timeout(120)
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"ABSENT", "STANDALONE", "CLUSTER"})
  void productionScan_redisMode_selectsExpectedDefinitions(String mode) {
    com.opencop.core.sdk.testing.config.SpringApplicationBootstrapAssertions
        .assertScannedRedisConfiguration(CoreApplication.createApplication(), mode, false);
  }
}
