// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

public class RedisHistoricalJobLifecycleStateRepository {
  private static final String GUARDED_UPDATE_SCRIPT_PATH =
      "redis/history/job_update_with_guards.lua";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_STAGE = "stage";
  private static final String FIELD_UPDATED_AT = "updatedAt";
  private static final String FIELD_EXPIRES_AT = "expiresAt";
  private static final String FIELD_FAILURE_REASON = "failureReason";
  private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");
  private static final String CANCELLATION_REQUESTED_STAGE = "CANCELLATION_REQUESTED";

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final RedisScript<Long> guardedUpdateScript;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  public RedisHistoricalJobLifecycleStateRepository(StringRedisTemplate redisTemplate) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE);
  }

  public RedisHistoricalJobLifecycleStateRepository(
      StringRedisTemplate redisTemplate, HistoricalJobControlKeyLayout keyLayout) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.guardedUpdateScript = guardedUpdateScript();
  }

  public boolean markCompleted(UUID jobId, Duration ttl) {
    if (redisTemplate == null) {
      return false;
    }
    Instant now = Instant.now();
    return guardedWrite(
        jobId,
        Map.of(
            FIELD_STATUS, LifecycleStatus.COMPLETED.name(),
            FIELD_STAGE, LifecycleStage.EXECUTION_COMPLETED.name(),
            FIELD_UPDATED_AT, now.toString(),
            FIELD_EXPIRES_AT, now.plus(ttl).toString(),
            FIELD_FAILURE_REASON, ""),
        ttl);
  }

  public boolean markFailed(UUID jobId, String failureReason, Duration ttl) {
    if (redisTemplate == null) {
      return false;
    }
    Instant now = Instant.now();
    return guardedWrite(
        jobId,
        Map.of(
            FIELD_STATUS,
            LifecycleStatus.FAILED.name(),
            FIELD_STAGE,
            LifecycleStage.DELIVERY_FAILED.name(),
            FIELD_UPDATED_AT,
            now.toString(),
            FIELD_EXPIRES_AT,
            now.plus(ttl).toString(),
            FIELD_FAILURE_REASON,
            failureReason == null ? "" : failureReason),
        ttl);
  }

  private boolean guardedWrite(UUID jobId, Map<String, String> values, Duration ttl) {
    List<String> arguments = new ArrayList<>();
    arguments.add(Integer.toString(TERMINAL_STATUSES.size()));
    arguments.addAll(TERMINAL_STATUSES);
    arguments.add("1");
    arguments.add(CANCELLATION_REQUESTED_STAGE);
    arguments.add(Long.toString(ttl.toMillis()));
    values.forEach(
        (field, value) -> {
          arguments.add(field);
          arguments.add(value);
        });
    Long updated =
        redisTemplate.execute(
            guardedUpdateScript, List.of(keyLayout.jobState(jobId)), arguments.toArray());
    return Long.valueOf(1L).equals(updated);
  }

  private static RedisScript<Long> guardedUpdateScript() {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(
        new ResourceScriptSource(new ClassPathResource(GUARDED_UPDATE_SCRIPT_PATH)));
    return script;
  }

  private enum LifecycleStatus {
    COMPLETED,
    FAILED
  }

  private enum LifecycleStage {
    EXECUTION_COMPLETED,
    DELIVERY_FAILED
  }
}
