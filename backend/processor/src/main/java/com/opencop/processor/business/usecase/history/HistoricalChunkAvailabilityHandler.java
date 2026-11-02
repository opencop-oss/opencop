// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReadyNotifier;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalChunkAvailabilityHandler {

  private static final String REASON_CACHED_PAYLOAD_MISSING = "CACHED_PAYLOAD_MISSING";
  private static final Logger log =
      LoggerFactory.getLogger(HistoricalChunkAvailabilityHandler.class);

  private final HistoricalChunkPersistenceGateway payloadPersistence;
  private final HistoricalChunkPayloadCacheGateway payloadCacheGateway;
  private final HistoricalChunkStateGateway chunkStateGateway;
  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalChunkReadyNotifier chunkReadyNotifier;
  private final HistoricalCacheIdentity cacheIdentity;

  public HistoricalChunkAvailabilityHandler(
      HistoricalChunkPersistenceGateway payloadPersistence,
      HistoricalChunkPayloadCacheGateway payloadCacheGateway,
      HistoricalChunkStateGateway chunkStateGateway,
      HistoricalJobStateGateway jobStateGateway,
      HistoricalChunkReadyNotifier chunkReadyNotifier,
      HistoricalCacheIdentity cacheIdentity) {
    this.payloadPersistence = payloadPersistence;
    this.payloadCacheGateway = payloadCacheGateway;
    this.chunkStateGateway = chunkStateGateway;
    this.jobStateGateway = jobStateGateway;
    this.chunkReadyNotifier = chunkReadyNotifier;
    this.cacheIdentity = cacheIdentity;
  }

  public boolean reuseCachedChunkIfAvailable(
      HistoricalExecutionContext executionContext, HistoricalChunkPlanEntry chunk) {
    HistoricalChunkState chunkState =
        chunkStateGateway.findByChunkStateKey(chunk.chunkStateKey()).orElse(null);
    int partCount = chunkState == null ? 1 : Math.max(1, chunkState.partCount());
    if (hasMissingCachedPart(chunk.payloadKey(), partCount)) {
      log.warn(
          "Historical cached chunk payload missing for job {} chunkIndex {} reasonCode {}; scheduling recompute",
          executionContext.jobId(),
          chunk.chunkIndex(),
          REASON_CACHED_PAYLOAD_MISSING);
      payloadPersistence.markPayloadMissingForRecovery(chunk, true);
      return false;
    }

    chunkStateGateway.transition(
        chunk.chunkStateKey(), HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY);
    jobStateGateway.refreshActiveTtl(executionContext.jobId());
    chunkReadyNotifier.notifyChunkReady(executionContext, chunk, true, partCount);
    return true;
  }

  public void notifyComputedChunkReady(
      HistoricalExecutionContext executionContext, HistoricalChunkPlanEntry chunk, int partCount) {
    chunkReadyNotifier.notifyChunkReady(executionContext, chunk, false, partCount);
  }

  public void markChunkFailed(HistoricalChunkPlanEntry chunk) {
    chunkStateGateway.transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.FAILED);
  }

  private boolean hasMissingCachedPart(String payloadKey, int partCount) {
    List<String> keys = new ArrayList<>(partCount);
    for (int partIndex = 0; partIndex < partCount; partIndex++) {
      keys.add(cacheIdentity.payloadPartKey(payloadKey, partIndex));
    }
    return payloadCacheGateway.findAll(keys).values().stream().anyMatch(Optional::isEmpty);
  }
}
