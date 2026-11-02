// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HistoricalJobStateRedisHashMapper {

  private static final String FIELD_VERSION = "version";
  private static final String FIELD_QUEUE_ROLE = "queueRole";
  private static final String FIELD_USER_SEQUENCE = "userSequence";
  private static final String FIELD_REQUEST_SEQUENCE = "requestSequence";
  private static final String CURRENT_VERSION = "1";
  private static final Logger log =
      LoggerFactory.getLogger(HistoricalJobStateRedisHashMapper.class);

  private HistoricalJobStateRedisHashMapper() {}

  static Map<String, String> toRedisHash(HistoricalJobState state) {
    Map<String, String> hash = new LinkedHashMap<>();
    hash.put(FIELD_VERSION, CURRENT_VERSION);
    hash.put("jobId", state.jobId().toString());
    hash.put("requestId", state.requestId().toString());
    hash.put("jobType", state.jobType());
    hash.put("subjectType", state.subjectType().name());
    hash.put("subjectId", state.subjectId().toString());
    hash.put("requestedBy", state.requestedBy().toString());
    hash.put("requestedAt", state.requestedAt().toString());
    hash.put("requestedFrom", state.requestedFrom().toString());
    hash.put("requestedTo", state.requestedTo().toString());
    hash.put("effectiveFrom", state.effectiveFrom().toString());
    hash.put("effectiveTo", state.effectiveTo().toString());
    hash.put("resolution", state.resolution().name());
    hash.put("plannedChunks", Long.toString(state.plannedChunks()));
    hash.put("status", state.status().name());
    hash.put("stage", state.stage().name());
    hash.put("startedAt", nullableInstant(state.startedAt()));
    hash.put("updatedAt", state.updatedAt().toString());
    hash.put("expiresAt", state.expiresAt().toString());
    hash.put("failureReason", nullable(state.failureReason()));
    hash.put("workerId", nullable(state.workerId()));
    hash.put(FIELD_USER_SEQUENCE, Long.toString(state.userSequence()));
    hash.put(FIELD_REQUEST_SEQUENCE, Long.toString(state.requestSequence()));
    hash.put(FIELD_QUEUE_ROLE, nullable(state.queueRole()));
    return hash;
  }

  static HistoricalJobState fromRedisHash(Map<Object, Object> hash) {
    return new HistoricalJobState(
        UUID.fromString(required(hash, "jobId")),
        UUID.fromString(required(hash, "requestId")),
        required(hash, "jobType"),
        SubjectType.valueOf(required(hash, "subjectType")),
        UUID.fromString(required(hash, "subjectId")),
        UUID.fromString(required(hash, "requestedBy")),
        Instant.parse(required(hash, "requestedAt")),
        Instant.parse(required(hash, "requestedFrom")),
        Instant.parse(required(hash, "requestedTo")),
        Instant.parse(required(hash, "effectiveFrom")),
        Instant.parse(required(hash, "effectiveTo")),
        HistoricalResolution.valueOf(required(hash, "resolution")),
        Long.parseLong(required(hash, "plannedChunks")),
        HistoricalJobStatus.valueOf(required(hash, "status")),
        stageOrLegacy(required(hash, "stage")),
        optionalInstant(hash, "startedAt"),
        Instant.parse(required(hash, "updatedAt")),
        Instant.parse(required(hash, "expiresAt")),
        optional(hash, "failureReason"),
        optional(hash, "workerId"),
        optionalLong(hash, FIELD_USER_SEQUENCE, 0L),
        optionalLong(hash, FIELD_REQUEST_SEQUENCE, 0L),
        optional(hash, FIELD_QUEUE_ROLE) == null ? "" : optional(hash, FIELD_QUEUE_ROLE));
  }

  /**
   * Resolve a persisted stage name, falling back to {@link
   * HistoricalJobStage#EXECUTION_FAILED_UNKNOWN} when the name is unknown to this binary. Covers
   * both pre-subdivision snapshots that carry the literal string {@code "EXECUTION_FAILED"} and
   * forward-compat with newer replicas that may write variants introduced in a later release.
   */
  private static HistoricalJobStage stageOrLegacy(String name) {
    try {
      return HistoricalJobStage.valueOf(name);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unknown HistoricalJobStage '{}' in persisted state; falling back to EXECUTION_FAILED_UNKNOWN",
          name);
      return HistoricalJobStage.EXECUTION_FAILED_UNKNOWN;
    }
  }

  private static String required(Map<Object, Object> hash, String field) {
    Object value = hash.get(field);
    if (value == null || value.toString().isBlank()) {
      throw new IllegalStateException("Missing historical job field " + field);
    }
    return value.toString();
  }

  private static String optional(Map<Object, Object> hash, String field) {
    Object value = hash.get(field);
    return value == null || value.toString().isBlank() ? null : value.toString();
  }

  private static Instant optionalInstant(Map<Object, Object> hash, String field) {
    String value = optional(hash, field);
    return value == null ? null : Instant.parse(value);
  }

  private static long optionalLong(Map<Object, Object> hash, String field, long fallback) {
    String value = optional(hash, field);
    return value == null ? fallback : Long.parseLong(value);
  }

  private static String nullable(String value) {
    return value == null ? "" : value;
  }

  private static String nullableInstant(Instant value) {
    return value == null ? "" : value.toString();
  }
}
