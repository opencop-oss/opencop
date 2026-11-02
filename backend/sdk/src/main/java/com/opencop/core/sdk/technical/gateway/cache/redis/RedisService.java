// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import com.opencop.core.sdk.ports.gateway.cache.CacheException;
import com.opencop.core.sdk.ports.gateway.cache.CacheExceptionCodes;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisService<I> implements CacheService<I> {
  private static final Logger log = LoggerFactory.getLogger(RedisService.class);
  private final Serializer<I, byte[]> serializer;
  private final RedisTemplate<String, byte[]> redisTemplate;
  private final StringRedisTemplate stringTemplate;

  public RedisService(
      final Serializer<I, byte[]> serializer,
      final RedisTemplate<String, byte[]> redisTemplate,
      final StringRedisTemplate stringTemplate) {
    this.serializer = serializer;
    this.redisTemplate = redisTemplate;
    this.stringTemplate = stringTemplate;
  }

  @Override
  public void set(String fullKey, I value, Duration ttl) throws CacheException {
    try {
      byte[] serialized = serializer.serialize(value);
      if (ttl != null && ttl.isPositive()) {
        redisTemplate.opsForValue().set(fullKey, serialized, ttl);
      } else {
        redisTemplate.opsForValue().set(fullKey, serialized);
      }
    } catch (Exception e) {
      log.warn(e.getMessage());
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }

  @Override
  public void set(String keyPrefix, String key, I value, Duration ttl) throws CacheException {
    String prefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    this.set(prefix + key, value, ttl);
  }

  @Override
  public void addToSet(String listKey, I value, double score) throws CacheException {
    try {
      redisTemplate.opsForZSet().add(listKey, serializer.serialize(value), score);
    } catch (Exception _) {
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }

  @Override
  public void addToSet(String listKey, String value, double score) throws CacheException {
    try {
      stringTemplate.opsForZSet().add(listKey, value, score);
    } catch (Exception _) {
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }

  @Override
  public void removeFromSet(String listKey, String eventKey) throws CacheException {
    try {
      stringTemplate.opsForZSet().remove(listKey, eventKey);
    } catch (Exception _) {
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }

  @Override
  public I get(String keyPrefix, String key, Class<I> clazz) throws CacheException {
    String prefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    return this.get(prefix + key, clazz);
  }

  @Override
  public I get(String fullKey, Class<I> clazz) throws CacheException {
    byte[] serialized = redisTemplate.opsForValue().get(fullKey);
    if (serialized == null) throw new CacheException(CacheExceptionCodes.VALUE_DOES_NOT_EXIST);
    try {
      return serializer.deserialize(serialized);
    } catch (Exception _) {
      throw new CacheException(CacheExceptionCodes.DESERIALIZATION_FAILED);
    }
  }

  @Override
  public byte[] getBytes(String keyPrefix, String key) throws CacheException {
    String prefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    String fullKey = prefix + key;

    return getBytes(fullKey);
  }

  @Override
  public byte[] getBytes(String fullKey) throws CacheException {
    return redisTemplate.opsForValue().get(fullKey);
  }

  @Override
  public Boolean deleteIfExists(String fullKey) {
    return redisTemplate.delete(fullKey);
  }

  @Override
  public Double getScore(String listKey, String objectId) {
    return stringTemplate.opsForZSet().score(listKey, objectId);
  }

  @Override
  public Set<String> getEventKeys(String listKey) throws CacheException {
    return stringTemplate.opsForZSet().range(listKey, 0, -1);
  }

  @Override
  public Set<String> getEventKeysByScore(
      String listKey, long min, long max, int offset, int pageSize) {
    return stringTemplate.opsForZSet().rangeByScore(listKey, min, max, offset, pageSize);
  }

  @Override
  public void executePipelined(RedisCallback<Object> callback) {
    this.redisTemplate.executePipelined(callback);
  }

  @Override
  public void setHash(String key, Map<String, String> hash, Duration ttl) throws CacheException {
    try {
      stringTemplate.opsForHash().putAll(key, hash);
      if (ttl != null && ttl.isPositive()) {
        stringTemplate.expire(key, ttl);
      }
    } catch (Exception e) {
      log.warn(e.getMessage());
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }

  @Override
  public Map<Object, Object> getHashEntries(String key) throws CacheException {
    try {
      return stringTemplate.opsForHash().entries(key);
    } catch (Exception e) {
      log.warn(e.getMessage());
      throw new CacheException(CacheExceptionCodes.DESERIALIZATION_FAILED);
    }
  }

  @Override
  public Object getHashField(String key, String field) throws CacheException {
    try {
      return stringTemplate.opsForHash().get(key, field);
    } catch (Exception e) {
      log.warn(e.getMessage());
      throw new CacheException(CacheExceptionCodes.DESERIALIZATION_FAILED);
    }
  }

  @Override
  public void addToStringSet(String key, String value) throws CacheException {
    try {
      stringTemplate.opsForSet().add(key, value);
    } catch (Exception e) {
      log.warn(e.getMessage());
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }

  @Override
  public void removeFromStringSet(String key, String value) throws CacheException {
    try {
      stringTemplate.opsForSet().remove(key, value);
    } catch (Exception e) {
      log.warn(e.getMessage());
      throw new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    }
  }
}
