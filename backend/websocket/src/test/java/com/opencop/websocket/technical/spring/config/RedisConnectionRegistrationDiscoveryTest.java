// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.core.io.support.SpringFactoriesLoader;

class RedisConnectionRegistrationDiscoveryTest {

  @Test
  void sdkMetadata_onWebSocketClasspath_discoversBothRedisRegistrations() {
    var loader = getClass().getClassLoader();

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
