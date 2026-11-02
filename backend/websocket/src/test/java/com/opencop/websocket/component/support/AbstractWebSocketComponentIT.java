// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.support;

import com.opencop.core.sdk.component.redis.RedisComponentSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = WebSocketComponentTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractWebSocketComponentIT extends AbstractWebSocketFixtureIT {

  @DynamicPropertySource
  static void registerWebSocketComponentProperties(DynamicPropertyRegistry registry) {
    registerCommonWebSocketComponentProperties(registry);
    RedisComponentSupport.registerRedisProperties(registry);
    registry.add("redis.ssl", () -> "false");
  }

  @BeforeEach
  void resetStandaloneRedis() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }
}
