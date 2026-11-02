// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.SourceObjectKey;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.V1LatestInBucketSampler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateReader;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalStateReuseExecutor {

  private static final String REASON_CHUNK_COMPUTE_FAILED = "CHUNK_COMPUTE_FAILED";
  private static final Logger log = LoggerFactory.getLogger(HistoricalStateReuseExecutor.class);

  private final HistoricalChunkStateReader stateReader;
  private final V1LatestInBucketSampler sampler;
  private final HistoricalChunkPayloadBuilder payloadBuilder;
  private final HistoricalChunkPersistenceGateway payloadPersistence;
  private final HistoricalChunkStateGateway chunkStateGateway;
  private final HistoricalChunkAvailabilityHandler availabilityHandler;

  public HistoricalStateReuseExecutor(
      HistoricalChunkStateReader stateReader,
      V1LatestInBucketSampler sampler,
      HistoricalChunkPayloadBuilder payloadBuilder,
      HistoricalChunkPersistenceGateway payloadPersistence,
      HistoricalChunkStateGateway chunkStateGateway,
      HistoricalChunkAvailabilityHandler availabilityHandler) {
    this.stateReader = stateReader;
    this.sampler = sampler;
    this.payloadBuilder = payloadBuilder;
    this.payloadPersistence = payloadPersistence;
    this.chunkStateGateway = chunkStateGateway;
    this.availabilityHandler = availabilityHandler;
  }

  public Session newSession() {
    return new Session();
  }

  public final class Session {
    private final Map<SourceObjectKey, HistoricalSourceEvent> activeState = new LinkedHashMap<>();
    private Instant previousChunkEndExclusive;

    public HistoricalChunkExecutionResult executeChunk(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlan analyzedPlan,
        HistoricalChunkPlanEntry chunk) {
      if (chunk.cacheStatus() == HistoricalChunkCacheStatus.CACHED
          && availabilityHandler.reuseCachedChunkIfAvailable(executionContext, chunk)) {
        activeState.clear();
        previousChunkEndExclusive = null;
        return HistoricalChunkExecutionResult.REUSED_FROM_CACHE;
      }

      chunkStateGateway.transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.COMPUTING);
      try {
        accumulateChunkState(executionContext, chunk);
        pruneInactive(activeState, chunk.from());
        var sampledChunk =
            sampler.sampleChunk(chunk, analyzedPlan.resolution(), activeState.values());
        var builtPayload = payloadBuilder.buildPayload(sampledChunk, chunk.payloadKey());
        payloadPersistence.persistComputedChunk(executionContext, chunk, builtPayload);
        int partCount = builtPayload.parts().size();
        availabilityHandler.notifyComputedChunkReady(executionContext, chunk, partCount);
        previousChunkEndExclusive = chunk.to();
        return HistoricalChunkExecutionResult.COMPUTED;
      } catch (Throwable e) {
        log.error(
            "Historical chunk computation failed for job {} chunkIndex {} reasonCode {}",
            executionContext.jobId(),
            chunk.chunkIndex(),
            REASON_CHUNK_COMPUTE_FAILED,
            e);
        availabilityHandler.markChunkFailed(chunk);
        if (e instanceof Error error) {
          throw error;
        }
        throw new IllegalStateException(e);
      }
    }

    private void accumulateChunkState(
        HistoricalExecutionContext executionContext, HistoricalChunkPlanEntry chunk) {
      if (previousChunkEndExclusive == null) {
        stateReader.readInitialState(executionContext, chunk, this::consumeStateRow);
        return;
      }

      stateReader.readStateDelta(
          executionContext, chunk, previousChunkEndExclusive, this::consumeStateRow);
    }

    private void consumeStateRow(HistoricalSourceEvent event) {
      HistoricalChunkInterruption.ensureNotInterrupted();
      activeState.put(new SourceObjectKey(event.sourceId(), event.objectId()), event);
    }
  }

  private static void pruneInactive(
      Map<SourceObjectKey, HistoricalSourceEvent> activeState, Instant chunkStartInclusive) {
    activeState
        .values()
        .removeIf(
            event -> event.staleAt() != null && !event.staleAt().isAfter(chunkStartInclusive));
  }
}
