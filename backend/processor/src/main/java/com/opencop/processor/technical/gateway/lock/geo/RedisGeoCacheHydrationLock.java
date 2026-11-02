// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.lock.geo;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.processor.ports.technical.gateway.lock.geo.GeoCacheHydrationLock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisGeoCacheHydrationLock implements GeoCacheHydrationLock {

  private static final String RELEASE_SCRIPT =
      """
      if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
      end
      return 0
      """;

  private final StringRedisTemplate stringRedisTemplate;
  private final EventGeoCacheKeyLayout keyLayout;

  public RedisGeoCacheHydrationLock(
      StringRedisTemplate stringRedisTemplate, EventGeoCacheKeyLayout keyLayout) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.keyLayout = keyLayout;
  }

  @Override
  public boolean tryAcquire(UUID sourceId, String owner, Duration ttl) {
    return Boolean.TRUE.equals(
        stringRedisTemplate
            .opsForValue()
            .setIfAbsent(keyLayout.sourceKeys(sourceId).hydrationLockKey(), owner, ttl));
  }

  @Override
  public void release(UUID sourceId, String owner) {
    stringRedisTemplate.execute(
        new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class),
        List.of(keyLayout.sourceKeys(sourceId).hydrationLockKey()),
        owner);
  }
}
