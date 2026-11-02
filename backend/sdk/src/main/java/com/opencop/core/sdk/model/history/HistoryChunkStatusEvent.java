// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record HistoryChunkStatusEvent(
    UUID targetUserId,
    UUID requestId,
    UUID jobId,
    SubjectType subjectType,
    UUID subjectId,
    String chunkKey,
    String chunkStateKey,
    int chunkIndex,
    String payloadKey,
    Instant chunkFrom,
    Instant chunkTo,
    HistoricalResolution resolution,
    String samplingVersion,
    boolean cached,
    int partCount,
    HistoryChunkStatus status,
    Map<String, String> traceContext)
    implements HistoryEvent {

  public static final HistoryEventType TYPE = HistoryEventType.HISTORY_CHUNK_STATUS;

  public HistoryChunkStatusEvent {
    traceContext = HistoricalTraceContext.sanitize(traceContext);
  }

  public HistoryChunkStatusEvent(
      UUID targetUserId,
      UUID requestId,
      UUID jobId,
      SubjectType subjectType,
      UUID subjectId,
      String chunkKey,
      String chunkStateKey,
      int chunkIndex,
      String payloadKey,
      Instant chunkFrom,
      Instant chunkTo,
      HistoricalResolution resolution,
      String samplingVersion,
      boolean cached,
      int partCount,
      HistoryChunkStatus status) {
    this(
        targetUserId,
        requestId,
        jobId,
        subjectType,
        subjectId,
        chunkKey,
        chunkStateKey,
        chunkIndex,
        payloadKey,
        chunkFrom,
        chunkTo,
        resolution,
        samplingVersion,
        cached,
        partCount,
        status,
        Map.of());
  }

  @Override
  @JsonProperty("type")
  public HistoryEventType type() {
    return TYPE;
  }
}
