// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
final class RedisHistoricalJobAdmissionMeta {

  private static final String OWNER_FIELD = "userId";

  private final StringRedisTemplate redisTemplate;
  private final RedisHistoricalJobAdmissionScriptGateway scriptGateway;
  private final HistoricalJobControlKeyLayout keyLayout;

  RedisHistoricalJobAdmissionMeta(
      StringRedisTemplate redisTemplate, RedisHistoricalJobAdmissionScriptGateway scriptGateway) {
    this.redisTemplate = redisTemplate;
    this.scriptGateway = scriptGateway;
    this.keyLayout = scriptGateway.keyLayout();
  }

  void recordOwner(UUID jobId, UUID userId) {
    String key = keyLayout.jobMeta(jobId);
    redisTemplate.opsForHash().put(key, OWNER_FIELD, userId.toString());
    redisTemplate.expire(key, scriptGateway.admissionTtl());
  }

  UUID findOwner(UUID jobId) {
    String key = keyLayout.jobMeta(jobId);
    String userValue = (String) redisTemplate.opsForHash().get(key, OWNER_FIELD);
    if (userValue == null) {
      return null;
    }
    UUID parsedUserId = parseUuid(userValue);
    if (parsedUserId == null) {
      redisTemplate.delete(key);
    }
    return parsedUserId;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
