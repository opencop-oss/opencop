// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkSampledResult;
import com.opencop.processor.business.policy.history.V1LatestInBucketSampler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReader;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalChunkComputationService {

  private static final String REASON_CHUNK_COMPUTE_FAILED = "CHUNK_COMPUTE_FAILED";
  private static final Logger log =
      LoggerFactory.getLogger(HistoricalChunkComputationService.class);

  private final HistoricalChunkReader chunkReader;
  private final V1LatestInBucketSampler sampler;
  private final HistoricalChunkPayloadBuilder payloadBuilder;
  private final HistoricalChunkPersistenceGateway payloadPersistence;
  private final HistoricalChunkStateGateway chunkStateGateway;
  private final HistoricalChunkAvailabilityHandler availabilityHandler;

  public HistoricalChunkComputationService(
      HistoricalChunkReader chunkReader,
      V1LatestInBucketSampler sampler,
      HistoricalChunkPayloadBuilder payloadBuilder,
      HistoricalChunkPersistenceGateway payloadPersistence,
      HistoricalChunkStateGateway chunkStateGateway,
      HistoricalChunkAvailabilityHandler availabilityHandler) {
    this.chunkReader = chunkReader;
    this.sampler = sampler;
    this.payloadBuilder = payloadBuilder;
    this.payloadPersistence = payloadPersistence;
    this.chunkStateGateway = chunkStateGateway;
    this.availabilityHandler = availabilityHandler;
  }

  void computeMissingChunk(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlan analyzedPlan,
      HistoricalChunkPlanEntry chunk) {
    chunkStateGateway.transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.COMPUTING);
    try {
      var sampledChunk = sampleChunk(executionContext, analyzedPlan, chunk);
      var builtPayload = payloadBuilder.buildPayload(sampledChunk, chunk.payloadKey());
      payloadPersistence.persistComputedChunk(executionContext, chunk, builtPayload);
      int partCount = builtPayload.parts().size();
      availabilityHandler.notifyComputedChunkReady(executionContext, chunk, partCount);
    } catch (Exception e) {
      log.error(
          "Historical chunk computation failed for job {} chunkIndex {} reasonCode {}",
          executionContext.jobId(),
          chunk.chunkIndex(),
          REASON_CHUNK_COMPUTE_FAILED,
          e);
      availabilityHandler.markChunkFailed(chunk);
      if (e instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(e);
    }
  }

  private HistoricalChunkSampledResult sampleChunk(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlan analyzedPlan,
      HistoricalChunkPlanEntry chunk) {
    var accumulator = sampler.newAccumulator(chunk, analyzedPlan.resolution());
    chunkReader.readChunk(
        executionContext,
        chunk,
        event -> {
          ensureNotInterrupted();
          accumulator.accept(event);
        });
    return accumulator.finish();
  }

  private static void ensureNotInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new IllegalStateException("Historical job interrupted during chunk execution");
    }
  }
}
