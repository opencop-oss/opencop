// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuildResult;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class HistoricalChunkPayloadPersistence implements HistoricalChunkPersistenceGateway {

  private final HistoricalChunkPayloadCacheGateway payloadCacheGateway;
  private final HistoricalChunkStateGateway chunkStateGateway;
  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalExecutionProperties properties;

  public HistoricalChunkPayloadPersistence(
      HistoricalChunkPayloadCacheGateway payloadCacheGateway,
      HistoricalChunkStateGateway chunkStateGateway,
      HistoricalJobStateGateway jobStateGateway,
      HistoricalExecutionProperties properties) {
    this.payloadCacheGateway = payloadCacheGateway;
    this.chunkStateGateway = chunkStateGateway;
    this.jobStateGateway = jobStateGateway;
    this.properties = properties;
  }

  public void persistComputedChunk(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      HistoricalChunkPayloadBuildResult payload) {
    Objects.requireNonNull(executionContext, "executionContext");
    Objects.requireNonNull(chunk, "chunk");
    Objects.requireNonNull(payload, "payload");

    for (var part : payload.parts()) {
      payloadCacheGateway.put(
          part.payloadKey(), part.serializedPayload(), properties.getPayloadTtl());
    }
    chunkStateGateway.updatePartCount(chunk.chunkStateKey(), payload.parts().size());
    chunkStateGateway.transition(chunk.chunkStateKey(), HistoricalChunkStateStatus.CACHED);
    chunkStateGateway.transition(
        chunk.chunkStateKey(), HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY);
    jobStateGateway.refreshActiveTtl(executionContext.jobId());
  }

  public void markPayloadMissingForRecovery(
      HistoricalChunkPlanEntry chunk, boolean recoveryPossible) {
    Objects.requireNonNull(chunk, "chunk");
    chunkStateGateway.transition(
        chunk.chunkStateKey(),
        recoveryPossible
            ? HistoricalChunkStateStatus.RECOMPUTE_NEEDED
            : HistoricalChunkStateStatus.FAILED);
  }
}
