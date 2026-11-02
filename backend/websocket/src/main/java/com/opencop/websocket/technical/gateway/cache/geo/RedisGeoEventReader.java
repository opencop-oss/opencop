// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.geo;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Reads selected-layout live geo indexes and their concrete payload keys. */
public final class RedisGeoEventReader implements GeoEventReader {

  private final EventGeoCacheKeyLayout layout;
  private final StringRedisTemplate stringRedisTemplate;
  private final RedisTemplate<String, byte[]> redisTemplate;

  public RedisGeoEventReader(
      EventGeoCacheKeyLayout layout,
      StringRedisTemplate stringRedisTemplate,
      RedisTemplate<String, byte[]> redisTemplate) {
    this.layout = Objects.requireNonNull(layout, "layout");
    this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
    this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
  }

  @Override
  public List<byte[]> readObjects(String sourceId, List<String> objectIds) {
    Objects.requireNonNull(objectIds, "objectIds");
    if (objectIds.isEmpty()) {
      return List.of();
    }

    var sourceKeys = layout.sourceKeys(sourceId);
    var payloadKeys = new ArrayList<String>(objectIds.size());
    for (var objectId : objectIds) {
      payloadKeys.add(sourceKeys.eventKey(objectId));
    }

    return List.copyOf(readExistingPayloads(payloadKeys));
  }

  @Override
  public Page readPage(String sourceId, long offset, int pageSize) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must not be negative");
    }
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive");
    }

    var sourceKeys = layout.sourceKeys(sourceId);
    var members =
        stringRedisTemplate
            .opsForZSet()
            .range(sourceKeys.updatedAtKey(), offset, offset + pageSize - 1L);
    if (members == null || members.isEmpty()) {
      return new Page(0, List.of());
    }

    var sourceEventPrefix = sourceKeys.eventPrefix() + ":";
    var payloadKeys = new ArrayList<String>(members.size());
    for (var member : members) {
      if (member.startsWith(sourceEventPrefix)) {
        payloadKeys.add(member);
      }
    }

    return new Page(members.size(), readExistingPayloads(payloadKeys));
  }

  private List<byte[]> readExistingPayloads(List<String> payloadKeys) {
    if (payloadKeys.isEmpty()) {
      return List.of();
    }

    var values = redisTemplate.opsForValue().multiGet(payloadKeys);
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    var payloads = new ArrayList<byte[]>(values.size());
    for (var value : values) {
      if (value != null) {
        payloads.add(value);
      }
    }
    return payloads;
  }
}
