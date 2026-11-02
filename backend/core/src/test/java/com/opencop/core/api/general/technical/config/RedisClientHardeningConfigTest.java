// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.common.AppConfig;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisClientHardeningConfig;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.SpringFactoriesLoader;

class RedisClientHardeningConfigTest {

  @Test
  void appConfig_leavesHardeningToDiscoverableSdkRegistrations() {
    var imported = AppConfig.class.getAnnotation(Import.class).value();
    var loader = getClass().getClassLoader();

    assertFalse(Arrays.asList(imported).contains(RedisClientHardeningConfig.class));
    assertTrue(
        ImportCandidates.load(AutoConfiguration.class, loader)
            .getCandidates()
            .contains(
                "com.opencop.core.sdk.technical.gateway.cache.redis.RedisConnectionAutoConfiguration"));
    assertTrue(
        SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, loader)
            .contains(
                "com.opencop.core.sdk.technical.gateway.cache.redis.RedisTopologyEnvironmentPostProcessor"));
  }
}
