// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisEventGeoCacheEvictionGateway implements EventGeoCacheEvictionGateway {

  private static final int SCAN_COUNT = 1000;

  private final StringRedisTemplate redisTemplate;
  private final EventGeoCacheKeyLayout layout;
  private final RedisEventGeoSourceDirectory sourceDirectory;

  public RedisEventGeoCacheEvictionGateway(
      StringRedisTemplate redisTemplate,
      EventGeoCacheKeyLayout layout,
      RedisEventGeoSourceDirectory sourceDirectory) {
    this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    this.layout = Objects.requireNonNull(layout, "layout");
    this.sourceDirectory = Objects.requireNonNull(sourceDirectory, "sourceDirectory");
  }

  @Override
  public void evictGeoSource(UUID geoSourceId) {
    var sourceKeys = layout.sourceKeys(geoSourceId);

    Set<String> updatedAtMembers =
        redisTemplate.opsForZSet().range(sourceKeys.updatedAtKey(), 0, -1);
    Set<String> staleAtMembers = redisTemplate.opsForZSet().range(sourceKeys.staleAtKey(), 0, -1);

    List<String> keysToDelete = new ArrayList<>();
    List<String> discoveredEventKeys = scanKeys(sourceKeys.eventScanPattern());
    if (discoveredEventKeys != null) {
      keysToDelete.addAll(discoveredEventKeys);
    }
    addSourceScopedEventKeys(keysToDelete, updatedAtMembers, sourceKeys.eventPrefix() + ":");
    addSourceScopedEventKeys(keysToDelete, staleAtMembers, sourceKeys.eventPrefix() + ":");
    keysToDelete.add(sourceKeys.updatedAtKey());
    keysToDelete.add(sourceKeys.staleAtKey());

    redisTemplate.delete(keysToDelete);
    sourceDirectory.unregisterIfEmpty(sourceKeys);
  }

  private static void addSourceScopedEventKeys(
      List<String> keysToDelete, Set<String> candidateKeys, String eventKeyPrefix) {
    if (candidateKeys == null) {
      return;
    }
    candidateKeys.stream().filter(key -> key.startsWith(eventKeyPrefix)).forEach(keysToDelete::add);
  }

  private List<String> scanKeys(String pattern) {
    return redisTemplate.execute(
        (RedisCallback<List<String>>)
            connection -> {
              List<String> keys = new ArrayList<>();
              try (var cursor =
                  connection
                      .keyCommands()
                      .scan(ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build())) {
                while (cursor.hasNext()) {
                  keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
              }
              return keys;
            });
  }
}
