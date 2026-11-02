// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public class RedisPublisher<T> implements CachePublisher<T> {

  private final ObjectMapper om;
  private final CacheChannel channel;
  private final StringRedisTemplate template;

  public RedisPublisher(ObjectMapper om, CacheChannel channel, StringRedisTemplate template) {
    this.om = om;
    this.channel = channel;
    this.template = template;
  }

  @Override
  public void publish(T event) {
    template.convertAndSend(channel.name(), om.writeValueAsString(event));
  }
}
