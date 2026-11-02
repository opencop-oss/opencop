// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.RedisCallback;

public interface CacheService<I> {

  void set(String fullKey, I value, Duration tll) throws CacheException;

  void set(String keyPrefix, String key, I value, Duration tll) throws CacheException;

  void addToSet(String listKey, String key, double score) throws CacheException;

  void addToSet(String listKey, I value, double score) throws CacheException;

  void removeFromSet(String listKey, String eventKey) throws CacheException;

  I get(String keyPrefix, String key, Class<I> clazz) throws CacheException;

  I get(String fullKey, Class<I> clazz) throws CacheException;

  byte[] getBytes(String keyPrefix, String key) throws CacheException;

  byte[] getBytes(String fullKey) throws CacheException;

  Double getScore(String listKey, String objectId);

  Boolean deleteIfExists(String fullKey);

  Set<String> getEventKeys(String listKey) throws CacheException;

  Set<String> getEventKeysByScore(String listKey, long min, long max, int offset, int pageSize);

  void executePipelined(RedisCallback<Object> callback);

  void setHash(String key, Map<String, String> hash, Duration ttl) throws CacheException;

  Map<Object, Object> getHashEntries(String key) throws CacheException;

  Object getHashField(String key, String field) throws CacheException;

  void addToStringSet(String key, String value) throws CacheException;

  void removeFromStringSet(String key, String value) throws CacheException;
}
