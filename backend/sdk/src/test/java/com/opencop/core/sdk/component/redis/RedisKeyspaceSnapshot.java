// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisTemplate;

/** Exact byte/string snapshot of the Redis string and sorted-set keys used by live geo state. */
public record RedisKeyspaceSnapshot(Map<String, Entry> entries) {

  public RedisKeyspaceSnapshot {
    entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }

  /** Captures the complete keyspace of a caller-owned standalone Redis fixture. */
  public static RedisKeyspaceSnapshot capture(RedisTemplate<String, byte[]> redis) {
    var keys = redis.keys("*");
    if (keys == null || keys.isEmpty()) {
      return new RedisKeyspaceSnapshot(Map.of());
    }

    Map<String, Entry> entries = new TreeMap<>();
    for (String key : keys) {
      var type = redis.type(key);
      var ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
      if (type == DataType.STRING) {
        entries.put(key, Entry.string(redis.opsForValue().get(key), ttlMillis));
      } else if (type == DataType.ZSET) {
        Map<String, Double> members = new TreeMap<>();
        var tuples = redis.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples != null) {
          for (var tuple : tuples) {
            var value = tuple.getValue();
            if (value != null) {
              members.put(new String(value, StandardCharsets.UTF_8), tuple.getScore());
            }
          }
        }
        entries.put(key, Entry.sortedSet(members, ttlMillis));
      } else {
        throw new IllegalStateException(
            "Redis keyspace snapshot does not support key " + key + " of type " + type);
      }
    }
    return new RedisKeyspaceSnapshot(entries);
  }

  /** One immutable string or sorted-set entry. */
  public record Entry(
      DataType type, byte[] value, Map<String, Double> sortedSetMembers, long ttlMillis) {

    public Entry {
      value = value == null ? null : Arrays.copyOf(value, value.length);
      sortedSetMembers = Collections.unmodifiableMap(new LinkedHashMap<>(sortedSetMembers));
    }

    static Entry string(byte[] value, long ttlMillis) {
      return new Entry(DataType.STRING, value, Map.of(), ttlMillis);
    }

    static Entry sortedSet(Map<String, Double> members, long ttlMillis) {
      return new Entry(DataType.ZSET, null, members, ttlMillis);
    }

    @Override
    public byte[] value() {
      return value == null ? null : Arrays.copyOf(value, value.length);
    }
  }
}
