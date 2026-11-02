// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Refreshes the TTL on an active job-state hash atomically. Only {@code updatedAt} and {@code
 * expiresAt} are written, so a concurrent guarded transition (cancellation, failure, completion,
 * timeout) cannot be clobbered by a stale snapshot. The terminal-status check happens inside the
 * Lua script, closing the read-modify-write race that a Java-side read + {@code persistWithTtl}
 * would otherwise leave open.
 */
final class RedisHistoricalJobStateTtlRefresher {

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final RedisScript<Long> refreshActiveTtlScript;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  RedisHistoricalJobStateTtlRefresher(
      StringRedisTemplate redisTemplate, RedisScript<Long> refreshActiveTtlScript) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, refreshActiveTtlScript);
  }

  RedisHistoricalJobStateTtlRefresher(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      RedisScript<Long> refreshActiveTtlScript) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.refreshActiveTtlScript = refreshActiveTtlScript;
  }

  boolean refresh(UUID jobId, Instant updatedAt, Instant expiresAt, long ttlMillis) {
    List<Object> args = new ArrayList<>(3 + HistoricalJobStatus.TERMINAL.size());
    args.add(updatedAt.toString());
    args.add(expiresAt.toString());
    args.add(Long.toString(ttlMillis));
    for (HistoricalJobStatus status : HistoricalJobStatus.TERMINAL) {
      args.add(status.name());
    }
    Long refreshed =
        redisTemplate.execute(
            refreshActiveTtlScript, List.of(keyLayout.jobState(jobId)), args.toArray());
    return refreshed != null && refreshed == 1L;
  }
}
