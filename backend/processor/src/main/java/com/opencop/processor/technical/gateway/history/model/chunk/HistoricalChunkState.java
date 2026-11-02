// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.chunk;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record HistoricalChunkState(
    UUID jobId,
    int chunkIndex,
    String chunkKey,
    String payloadKey,
    String chunkStateKey,
    int partCount,
    HistoricalChunkStateStatus status,
    Instant from,
    Instant to,
    Instant updatedAt,
    Instant expiresAt) {

  private static final String FIELD_VERSION = "version";
  private static final String CURRENT_VERSION = "1";

  public static HistoricalChunkState initialize(
      UUID jobId,
      int chunkIndex,
      String chunkKey,
      String payloadKey,
      String chunkStateKey,
      int partCount,
      HistoricalChunkStateStatus status,
      Instant from,
      Instant to,
      Instant now,
      Duration ttl) {
    return new HistoricalChunkState(
        jobId,
        chunkIndex,
        chunkKey,
        payloadKey,
        chunkStateKey,
        partCount,
        status,
        from,
        to,
        now,
        now.plus(ttl));
  }

  public HistoricalChunkState update(
      HistoricalChunkStateStatus newStatus, Instant now, Duration ttl) {
    return new HistoricalChunkState(
        jobId,
        chunkIndex,
        chunkKey,
        payloadKey,
        chunkStateKey,
        partCount,
        newStatus,
        from,
        to,
        now,
        now.plus(ttl));
  }

  public HistoricalChunkState updatePartCount(int newPartCount, Instant now, Duration ttl) {
    return new HistoricalChunkState(
        jobId,
        chunkIndex,
        chunkKey,
        payloadKey,
        chunkStateKey,
        newPartCount,
        status,
        from,
        to,
        now,
        now.plus(ttl));
  }

  public Map<String, String> toRedisHash() {
    Map<String, String> hash = new LinkedHashMap<>();
    hash.put(FIELD_VERSION, CURRENT_VERSION);
    hash.put("jobId", jobId.toString());
    hash.put("chunkIndex", Integer.toString(chunkIndex));
    hash.put("chunkKey", chunkKey);
    hash.put("payloadKey", payloadKey);
    hash.put("chunkStateKey", chunkStateKey);
    hash.put("partCount", Integer.toString(partCount));
    hash.put("status", status.name());
    hash.put("from", from.toString());
    hash.put("to", to.toString());
    hash.put("updatedAt", updatedAt.toString());
    hash.put("expiresAt", expiresAt.toString());
    return hash;
  }

  public static HistoricalChunkState fromRedisHash(Map<Object, Object> hash) {
    return new HistoricalChunkState(
        UUID.fromString(required(hash, "jobId")),
        Integer.parseInt(required(hash, "chunkIndex")),
        required(hash, "chunkKey"),
        required(hash, "payloadKey"),
        required(hash, "chunkStateKey"),
        Integer.parseInt(required(hash, "partCount")),
        HistoricalChunkStateStatus.valueOf(required(hash, "status")),
        Instant.parse(required(hash, "from")),
        Instant.parse(required(hash, "to")),
        Instant.parse(required(hash, "updatedAt")),
        Instant.parse(required(hash, "expiresAt")));
  }

  private static String required(Map<Object, Object> hash, String field) {
    Object value = hash.get(field);
    if (value == null || value.toString().isBlank()) {
      throw new IllegalStateException("Missing historical chunk field " + field);
    }
    return value.toString();
  }
}
