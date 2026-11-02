// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class RedisComponentSupport {

  private RedisComponentSupport() {}

  public static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("redis.host", LocalRedisSupport::host);
    registry.add("redis.port", LocalRedisSupport::port);
  }
}
