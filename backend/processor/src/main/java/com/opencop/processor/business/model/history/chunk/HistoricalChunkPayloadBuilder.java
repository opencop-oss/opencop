// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.opencop.processor.business.model.history.SourceObjectKey;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import tools.jackson.databind.ObjectMapper;

public class HistoricalChunkPayloadBuilder {
  private final HistoricalChunkPointPayloadMapper pointPayloadMapper;
  private final HistoricalChunkPayloadPartitioner payloadPartitioner;
  private final HistoricalCacheIdentity cacheIdentity;
  private final HistoricalChunkPayloadBudget budget;

  HistoricalChunkPayloadBuilder(
      HistoricalChunkPointPayloadMapper pointPayloadMapper,
      HistoricalChunkPayloadPartitioner payloadPartitioner,
      HistoricalCacheIdentity cacheIdentity,
      HistoricalChunkPayloadBudget budget) {
    this.pointPayloadMapper = pointPayloadMapper;
    this.payloadPartitioner = payloadPartitioner;
    this.cacheIdentity = cacheIdentity;
    this.budget = budget;
  }

  /** Convenience constructor that wires the default collaborators; used by tests and subclasses. */
  protected HistoricalChunkPayloadBuilder(
      ObjectMapper objectMapper,
      HistoricalCacheIdentity cacheIdentity,
      int maxPayloadPartBytes,
      int maxChunkPayloadBytes) {
    this(
        new HistoricalChunkPointPayloadMapper(objectMapper),
        new HistoricalChunkPayloadPartitioner(maxPayloadPartBytes),
        cacheIdentity,
        new HistoricalChunkPayloadBudget(maxPayloadPartBytes, maxChunkPayloadBytes));
  }

  public static HistoricalChunkPayloadBuilder create(
      ObjectMapper objectMapper,
      HistoricalCacheIdentity cacheIdentity,
      int maxPayloadPartBytes,
      int maxChunkPayloadBytes) {
    return new HistoricalChunkPayloadBuilder(
        objectMapper, cacheIdentity, maxPayloadPartBytes, maxChunkPayloadBytes);
  }

  public HistoricalChunkPayloadBuildResult buildPayload(
      HistoricalChunkSampledResult sampledChunk, String basePayloadKey) {
    List<HistoricalChunkPointPayload> builtPoints =
        sampledChunk.points().stream().map(pointPayloadMapper::map).toList();
    List<List<HistoricalChunkPointPayload>> partitions =
        payloadPartitioner.partition(sampledChunk, builtPoints);
    int partCount = partitions.size();
    List<HistoricalChunkPayloadPart> parts = new ArrayList<>(partCount);

    for (int partIndex = 0; partIndex < partCount; partIndex++) {
      List<HistoricalChunkPointPayload> partPoints = partitions.get(partIndex);
      boolean finalForChunk = partIndex == (partCount - 1);
      String payloadKey = cacheIdentity.payloadPartKey(basePayloadKey, partIndex);
      HistoricalChunkPayload payload =
          HistoricalChunkPayloadPrototype.prefixBuilder(
                  sampledChunk,
                  partIndex,
                  partCount,
                  partPoints.size(),
                  distinctObjectCount(partPoints),
                  finalForChunk)
              .addAllElements(
                  partPoints.stream().map(HistoricalChunkPointPayload::message).toList())
              .build();

      byte[] serialized = payload.toByteArray();
      if (serialized.length > budget.maxPartBytes()) {
        throw new HistoricalChunkPayloadSizeException(
            sampledChunk.chunkKey(),
            "Historical payload part exceeds configured max payload part bytes for chunk "
                + sampledChunk.chunkKey());
      }
      parts.add(
          new HistoricalChunkPayloadPart(
              payloadKey,
              payload,
              serialized,
              serialized.length,
              partIndex,
              partCount,
              finalForChunk));
    }

    int totalSerializedBytes =
        parts.stream().mapToInt(HistoricalChunkPayloadPart::serializedSizeBytes).sum();
    if (totalSerializedBytes > budget.maxChunkBytes()) {
      throw new HistoricalChunkPayloadSizeException(
          sampledChunk.chunkKey(),
          "Historical chunk payload exceeds configured max chunk payload bytes for chunk "
              + sampledChunk.chunkKey());
    }

    return new HistoricalChunkPayloadBuildResult(parts);
  }

  private static int distinctObjectCount(List<HistoricalChunkPointPayload> points) {
    Set<SourceObjectKey> seen = new HashSet<>(points.size());
    for (HistoricalChunkPointPayload point : points) {
      seen.add(point.sourceObjectKey());
    }
    return seen.size();
  }
}
