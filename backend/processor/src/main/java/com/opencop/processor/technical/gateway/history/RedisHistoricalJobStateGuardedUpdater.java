// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

final class RedisHistoricalJobStateGuardedUpdater {

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final RedisScript<Long> guardedUpdateScript;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  RedisHistoricalJobStateGuardedUpdater(
      StringRedisTemplate redisTemplate, RedisScript<Long> guardedUpdateScript) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, guardedUpdateScript);
  }

  RedisHistoricalJobStateGuardedUpdater(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      RedisScript<Long> guardedUpdateScript) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.guardedUpdateScript = guardedUpdateScript;
  }

  boolean update(
      UUID jobId,
      HistoricalJobState targetState,
      List<String> blockedStatuses,
      List<String> blockedStages,
      long ttlMillis) {
    List<String> arguments = new ArrayList<>();
    arguments.add(Integer.toString(blockedStatuses.size()));
    arguments.addAll(blockedStatuses);
    arguments.add(Integer.toString(blockedStages.size()));
    arguments.addAll(blockedStages);
    arguments.add(ttlMillis > 0 ? Long.toString(ttlMillis) : "");
    targetState
        .toRedisHash()
        .forEach(
            (field, value) -> {
              arguments.add(field);
              arguments.add(value);
            });
    Long updated =
        redisTemplate.execute(
            guardedUpdateScript, List.of(keyLayout.jobState(jobId)), arguments.toArray());
    return updated != null && updated == 1L;
  }
}
