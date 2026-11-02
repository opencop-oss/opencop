// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.support;

import com.opencop.core.sdk.component.redis.RedisComponentSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractProcessorKafkaComponentIT {

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    RedisComponentSupport.registerRedisProperties(registry);
  }
}
