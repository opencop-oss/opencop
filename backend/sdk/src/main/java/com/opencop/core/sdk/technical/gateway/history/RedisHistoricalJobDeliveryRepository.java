// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.technical.model.history.HistoricalJobDeliveryRedisKeys;
import com.opencop.core.sdk.technical.model.history.HistoricalJobDeliverySnapshot;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis-backed coordination of per-job delivery counters and execution terminal state (Slice 14).
 */
public class RedisHistoricalJobDeliveryRepository {

  private static final String F_TARGET_USER = "uid";
  private static final String F_REQUEST = "rid";
  private static final String F_SUBJECT_TYPE = "st";
  private static final String F_SUBJECT_ID = "sid";
  private static final String F_TOTAL = "tc";
  private static final String F_CACHED = "cc";
  private static final String F_COMPUTED = "comp";
  private static final String F_FAILED = "fail";
  private static final String F_DELIVERY_FAILED = "deliveryFail";
  private static final String F_EXECUTION_COMPLETE = "ex";
  private static final String F_USER_LIFECYCLE = "ul";
  private static final String F_FAILURE_REASON = "fr";

  private final StringRedisTemplate redisTemplate;
  private final HashOperations<String, String, String> hashOps;
  private final ValueOperations<String, String> valueOps;

  public RedisHistoricalJobDeliveryRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.hashOps = redisTemplate == null ? null : redisTemplate.opsForHash();
    this.valueOps = redisTemplate == null ? null : redisTemplate.opsForValue();
  }

  public void ensureInitialized(
      UUID jobId,
      UUID targetUserId,
      UUID requestId,
      SubjectType subjectType,
      UUID subjectId,
      long totalChunks,
      long cachedChunks,
      boolean userLifecycle,
      Duration ttl) {
    String hashKey = HistoricalJobDeliveryRedisKeys.stateHashKey(jobId);
    String deliveredKey = HistoricalJobDeliveryRedisKeys.deliveredCounterKey(jobId);
    if (Boolean.TRUE.equals(redisTemplate.hasKey(hashKey))) {
      touchTtl(hashKey, deliveredKey, ttl);
      return;
    }
    redisTemplate.delete(deliveredKey);
    redisTemplate.delete(HistoricalJobDeliveryRedisKeys.completionClaimKey(jobId));
    redisTemplate.delete(HistoricalJobDeliveryRedisKeys.failureClaimKey(jobId));
    Map<String, String> initialState = new LinkedHashMap<>();
    initialState.put(F_TARGET_USER, targetUserId.toString());
    initialState.put(F_REQUEST, requestId.toString());
    initialState.put(F_SUBJECT_TYPE, subjectType.name());
    initialState.put(F_SUBJECT_ID, subjectId.toString());
    initialState.put(F_TOTAL, Long.toString(totalChunks));
    initialState.put(F_CACHED, Long.toString(cachedChunks));
    initialState.put(F_COMPUTED, "0");
    initialState.put(F_FAILED, "0");
    initialState.put(F_DELIVERY_FAILED, "0");
    initialState.put(F_EXECUTION_COMPLETE, "0");
    initialState.put(F_USER_LIFECYCLE, userLifecycle ? "1" : "0");
    initialState.put(F_FAILURE_REASON, "");
    hashOps.putAll(hashKey, initialState);
    valueOps.set(deliveredKey, "0", ttl);
    redisTemplate.expire(hashKey, ttl);
  }

  public void recordExecutionProgress(
      UUID jobId, long computedChunks, long failedChunks, Duration ttl) {
    String hashKey = HistoricalJobDeliveryRedisKeys.stateHashKey(jobId);
    if (Boolean.FALSE.equals(redisTemplate.hasKey(hashKey))) {
      return;
    }
    hashOps.put(hashKey, F_COMPUTED, Long.toString(computedChunks));
    hashOps.put(hashKey, F_FAILED, Long.toString(failedChunks));
    touchTtl(hashKey, HistoricalJobDeliveryRedisKeys.deliveredCounterKey(jobId), ttl);
  }

  public void markExecutionComplete(
      UUID jobId, long computedChunks, long failedChunks, Duration ttl) {
    String hashKey = HistoricalJobDeliveryRedisKeys.stateHashKey(jobId);
    if (Boolean.FALSE.equals(redisTemplate.hasKey(hashKey))) {
      return;
    }
    hashOps.put(hashKey, F_COMPUTED, Long.toString(computedChunks));
    hashOps.put(hashKey, F_FAILED, Long.toString(failedChunks));
    hashOps.put(hashKey, F_EXECUTION_COMPLETE, "1");
    touchTtl(hashKey, HistoricalJobDeliveryRedisKeys.deliveredCounterKey(jobId), ttl);
  }

  /**
   * @return new delivered count, or {@code -1} when this job has no delivery state (non-user jobs).
   */
  public long incrementDeliveredIfTracked(UUID jobId, Duration ttl) {
    String hashKey = HistoricalJobDeliveryRedisKeys.stateHashKey(jobId);
    if (!Boolean.TRUE.equals(redisTemplate.hasKey(hashKey))) {
      return -1L;
    }
    String deliveredKey = HistoricalJobDeliveryRedisKeys.deliveredCounterKey(jobId);
    Long v = valueOps.increment(deliveredKey);
    redisTemplate.expire(deliveredKey, ttl);
    redisTemplate.expire(hashKey, ttl);
    return v == null ? -1L : v;
  }

  public long incrementFailedIfTracked(UUID jobId, Duration ttl, String failureReason) {
    String hashKey = HistoricalJobDeliveryRedisKeys.stateHashKey(jobId);
    if (!Boolean.TRUE.equals(redisTemplate.hasKey(hashKey))) {
      return -1L;
    }
    Long v = hashOps.increment(hashKey, F_DELIVERY_FAILED, 1L);
    hashOps.put(hashKey, F_FAILURE_REASON, failureReason == null ? "" : failureReason);
    touchTtl(hashKey, HistoricalJobDeliveryRedisKeys.deliveredCounterKey(jobId), ttl);
    return v == null ? -1L : v;
  }

  public Optional<HistoricalJobDeliverySnapshot> loadSnapshot(UUID jobId) {
    String hashKey = HistoricalJobDeliveryRedisKeys.stateHashKey(jobId);
    Map<String, String> entries = hashOps.entries(hashKey);
    if (entries == null || entries.isEmpty()) {
      return Optional.empty();
    }
    String deliveredRaw = valueOps.get(HistoricalJobDeliveryRedisKeys.deliveredCounterKey(jobId));
    long delivered = parseLong(deliveredRaw, 0L);
    long executionFailed = parseLong(entries.get(F_FAILED), 0L);
    long deliveryFailed = parseLong(entries.get(F_DELIVERY_FAILED), 0L);
    try {
      return Optional.of(
          new HistoricalJobDeliverySnapshot(
              UUID.fromString(entries.get(F_TARGET_USER)),
              UUID.fromString(entries.get(F_REQUEST)),
              jobId,
              SubjectType.valueOf(entries.get(F_SUBJECT_TYPE)),
              UUID.fromString(entries.get(F_SUBJECT_ID)),
              parseLong(entries.get(F_TOTAL), 0L),
              parseLong(entries.get(F_CACHED), 0L),
              parseLong(entries.get(F_COMPUTED), 0L),
              executionFailed + deliveryFailed,
              delivered,
              "1".equals(entries.get(F_EXECUTION_COMPLETE)),
              "1".equals(entries.get(F_USER_LIFECYCLE)),
              entries.get(F_FAILURE_REASON)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  public boolean tryClaimCompletionPublication(UUID jobId, Duration ttl) {
    if (hasFailurePublicationClaim(jobId) || hasDeliveryFailure(jobId)) {
      return false;
    }
    Boolean ok =
        valueOps.setIfAbsent(HistoricalJobDeliveryRedisKeys.completionClaimKey(jobId), "1", ttl);
    return Boolean.TRUE.equals(ok);
  }

  public boolean tryClaimFailurePublication(UUID jobId, Duration ttl) {
    if (hasCompletionPublicationClaim(jobId)) {
      return false;
    }
    Boolean ok =
        valueOps.setIfAbsent(HistoricalJobDeliveryRedisKeys.failureClaimKey(jobId), "1", ttl);
    return Boolean.TRUE.equals(ok);
  }

  public boolean hasDeliveryFailure(UUID jobId) {
    String raw =
        (String) hashOps.get(HistoricalJobDeliveryRedisKeys.stateHashKey(jobId), F_DELIVERY_FAILED);
    return parseLong(raw, 0L) > 0L;
  }

  public boolean hasCompletionPublicationClaim(UUID jobId) {
    return Boolean.TRUE.equals(
        redisTemplate.hasKey(HistoricalJobDeliveryRedisKeys.completionClaimKey(jobId)));
  }

  public boolean hasFailurePublicationClaim(UUID jobId) {
    return Boolean.TRUE.equals(
        redisTemplate.hasKey(HistoricalJobDeliveryRedisKeys.failureClaimKey(jobId)));
  }

  private void touchTtl(String hashKey, String deliveredKey, Duration ttl) {
    redisTemplate.expire(hashKey, ttl);
    if (Boolean.TRUE.equals(redisTemplate.hasKey(deliveredKey))) {
      redisTemplate.expire(deliveredKey, ttl);
    }
  }

  private static long parseLong(String raw, long defaultValue) {
    if (raw == null || raw.isEmpty()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
