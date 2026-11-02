// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical;

import static com.opencop.core.sdk.testing.config.SpringApplicationBootstrapAssertions.assertConfiguredApplication;

import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import org.junit.jupiter.api.Test;

class WebSocketApplicationTest {

  @Test
  void configuresTheProductionApplicationBootstrap() {
    assertConfiguredApplication(
        WebSocketApplication.createApplication(),
        FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE,
        WebSocketApplication.class);
  }

  @org.junit.jupiter.api.Timeout(120)
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"ABSENT", "STANDALONE", "CLUSTER"})
  void productionScan_redisMode_selectsExpectedDefinitions(String mode) {
    com.opencop.core.sdk.testing.config.SpringApplicationBootstrapAssertions
        .assertScannedRedisConfiguration(WebSocketApplication.createApplication(), mode, true);
  }

  @org.junit.jupiter.api.Timeout(120)
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "' cLuStEr ', CLUSTER",
    "' ', STANDALONE",
    "' standalone ', STANDALONE"
  })
  void productionScan_nonCanonicalRedisMode_keepsRepairAlignedWithTopology(
      String configuredMode, String expectedMode) {
    com.opencop.core.sdk.testing.config.SpringApplicationBootstrapAssertions
        .assertScannedRedisConfiguration(
            WebSocketApplication.createApplication(), configuredMode, expectedMode, true);
  }
}
