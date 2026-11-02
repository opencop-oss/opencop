// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

public class RedisHistoricalChunkDeliveryStateRepository {

  private static final String CHUNK_MARK_DELIVERY_TERMINAL_SCRIPT_PATH =
      "redis/history/chunk_mark_delivery_terminal.lua";
  private static final String STATUS_AVAILABLE_FOR_DELIVERY = "AVAILABLE_FOR_DELIVERY";
  private static final String STATUS_DELIVERED = "DELIVERED";
  private static final String STATUS_FAILED = "FAILED";
  private static final String CHUNK_STATE_PREFIX = "opencop:history:chunk-state:";
  private static final String JOB_DELIVERY_MARKER_PREFIX = "opencop:history:chunk-delivery-marker:";

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> markDeliveryTerminalScript;

  public RedisHistoricalChunkDeliveryStateRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.markDeliveryTerminalScript = terminalMarkScript();
  }

  public void markDelivered(String chunkStateKey, Duration ttl) {
    tryMarkDelivered(chunkStateKey, ttl);
  }

  public boolean tryMarkDelivered(String chunkStateKey, Duration ttl) {
    return markTerminalState(chunkStateKey, STATUS_DELIVERED, "", ttl);
  }

  public boolean tryMarkDeliveredForJob(UUID jobId, String chunkStateKey, Duration ttl) {
    markTerminalState(chunkStateKey, STATUS_DELIVERED, "", ttl);
    return markJobDelivery(jobId, chunkStateKey, STATUS_DELIVERED, ttl);
  }

  public void markFailed(String chunkStateKey, Duration ttl) {
    tryMarkFailed(chunkStateKey, ttl, "");
  }

  public boolean tryMarkFailed(String chunkStateKey, Duration ttl, String failureReason) {
    return markTerminalState(chunkStateKey, STATUS_FAILED, failureReason, ttl);
  }

  public boolean tryMarkFailedForJob(
      UUID jobId, String chunkStateKey, Duration ttl, String failureReason) {
    markTerminalState(chunkStateKey, STATUS_FAILED, failureReason, ttl);
    return markJobDelivery(jobId, chunkStateKey, STATUS_FAILED, ttl);
  }

  private boolean markTerminalState(
      String chunkStateKey, String terminalStatus, String failureReason, Duration ttl) {
    if (redisTemplate == null || chunkStateKey == null || ttl == null) {
      return false;
    }
    if (!isAllowedChunkStateKey(chunkStateKey)) {
      return false;
    }

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Long changed =
        redisTemplate.execute(
            markDeliveryTerminalScript,
            List.of(chunkStateKey),
            CHUNK_STATE_PREFIX,
            STATUS_AVAILABLE_FOR_DELIVERY,
            terminalStatus,
            failureReason == null ? "" : failureReason,
            now.toString(),
            now.plus(ttl).toString(),
            Long.toString(ttl.toMillis()));
    return Long.valueOf(1L).equals(changed);
  }

  private boolean isAllowedChunkStateKey(String chunkStateKey) {
    return chunkStateKey.startsWith(CHUNK_STATE_PREFIX);
  }

  private boolean markJobDelivery(UUID jobId, String chunkStateKey, String status, Duration ttl) {
    if (redisTemplate == null || jobId == null || chunkStateKey == null || ttl == null) {
      return false;
    }
    if (!isAllowedChunkStateKey(chunkStateKey)) {
      return false;
    }
    Boolean changed =
        redisTemplate
            .opsForValue()
            .setIfAbsent(jobDeliveryMarkerKey(jobId, chunkStateKey), status, ttl);
    return Boolean.TRUE.equals(changed);
  }

  private static String jobDeliveryMarkerKey(UUID jobId, String chunkStateKey) {
    return JOB_DELIVERY_MARKER_PREFIX + jobId + ":" + sha256Hex(chunkStateKey);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 digest is not available", e);
    }
  }

  private static RedisScript<Long> terminalMarkScript() {
    var script = new DefaultRedisScript<Long>();
    script.setResultType(Long.class);
    script.setScriptSource(
        new ResourceScriptSource(new ClassPathResource(CHUNK_MARK_DELIVERY_TERMINAL_SCRIPT_PATH)));
    return script;
  }
}
