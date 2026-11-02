// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HistoricalChunkPlan(
    String planningVersion,
    UUID requestId,
    UUID jobId,
    SubjectType subjectType,
    UUID subjectId,
    UUID requestedBy,
    Instant requestedFrom,
    Instant requestedTo,
    Instant effectiveFrom,
    Instant effectiveTo,
    HistoricalResolution resolution,
    Instant requestedAt,
    long chunks,
    long cachedChunks,
    long chunksToCompute,
    List<HistoricalChunkPlanEntry> entries) {

  public static final String PLANNING_VERSION = "v1";

  public HistoricalChunkPlan {
    entries = List.copyOf(entries);
  }

  public static HistoricalChunkPlan forSubject(
      HistoricalSubjectExecutionMeta metadata, HistoricalChunkWindowPlan sharedWindowPlan) {
    return new HistoricalChunkPlan(
        PLANNING_VERSION,
        metadata.requestId(),
        metadata.laneJobId(),
        metadata.subjectType(),
        metadata.subjectId(),
        metadata.requestedBy(),
        metadata.requestedFrom(),
        metadata.requestedTo(),
        metadata.effectiveFrom(),
        metadata.effectiveTo(),
        metadata.resolution(),
        metadata.requestedAt(),
        sharedWindowPlan.chunks(),
        countByStatus(sharedWindowPlan.entries(), HistoricalChunkCacheStatus.CACHED),
        countByStatus(sharedWindowPlan.entries(), HistoricalChunkCacheStatus.MISSING),
        sharedWindowPlan.entries());
  }

  public HistoricalChunkPlan withEntries(List<HistoricalChunkPlanEntry> updatedEntries) {
    return new HistoricalChunkPlan(
        planningVersion,
        requestId,
        jobId,
        subjectType,
        subjectId,
        requestedBy,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        requestedAt,
        updatedEntries.size(),
        countByStatus(updatedEntries, HistoricalChunkCacheStatus.CACHED),
        countByStatus(updatedEntries, HistoricalChunkCacheStatus.MISSING),
        updatedEntries);
  }

  private static long countByStatus(
      List<HistoricalChunkPlanEntry> entries, HistoricalChunkCacheStatus status) {
    return entries.stream().filter(entry -> entry.cacheStatus() == status).count();
  }
}
