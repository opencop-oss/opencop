// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

final class RedisHistoricalJobCancellationRequestUpdater {

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final RedisScript<Long> markCancellationRequestedScript;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  RedisHistoricalJobCancellationRequestUpdater(
      StringRedisTemplate redisTemplate, RedisScript<Long> markCancellationRequestedScript) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, markCancellationRequestedScript);
  }

  RedisHistoricalJobCancellationRequestUpdater(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      RedisScript<Long> markCancellationRequestedScript) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.markCancellationRequestedScript = markCancellationRequestedScript;
  }

  boolean markRequested(
      UUID jobId,
      String reason,
      Instant updatedAt,
      Instant expiresAt,
      List<String> blockedStatuses,
      long ttlMillis) {
    List<String> arguments = new ArrayList<>();
    arguments.add(Integer.toString(blockedStatuses.size()));
    arguments.addAll(blockedStatuses);
    arguments.add(HistoricalJobStage.CANCELLATION_REQUESTED.name());
    arguments.add(reason);
    arguments.add(updatedAt.toString());
    arguments.add(expiresAt.toString());
    arguments.add(Long.toString(ttlMillis));
    Long updated =
        redisTemplate.execute(
            markCancellationRequestedScript,
            List.of(keyLayout.jobState(jobId)),
            arguments.toArray());
    return updated != null && updated == 1L;
  }
}
