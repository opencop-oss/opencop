// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import java.util.ArrayList;
import java.util.List;

final class HistoricalChunkPayloadPartitioner {

  private final int maxPayloadPartBytes;

  HistoricalChunkPayloadPartitioner(int maxPayloadPartBytes) {
    this.maxPayloadPartBytes = maxPayloadPartBytes;
  }

  List<List<HistoricalChunkPointPayload>> partition(
      HistoricalChunkSampledResult sampledChunk, List<HistoricalChunkPointPayload> builtPoints) {
    if (builtPoints.isEmpty()) {
      return List.of(List.of());
    }

    int maxPointsPerChunk = builtPoints.size();
    // Reserve a conservative metadata budget for every part — worst-case for the partIndex /
    // partCount varints means real parts always emit fewer bytes than reserved, so the post-build
    // size check in the builder can never overflow because of the prefix.
    int maxMetadataBytes =
        worstCaseMetadataBytes(sampledChunk, maxPointsPerChunk, sampledChunk.objectCount());
    List<List<HistoricalChunkPointPayload>> parts = new ArrayList<>();
    List<HistoricalChunkPointPayload> currentPart = new ArrayList<>();
    int currentSizeBytes = maxMetadataBytes;
    for (HistoricalChunkPointPayload point : builtPoints) {
      int singlePointSize = maxMetadataBytes + point.serializedSizeBytes();
      if (singlePointSize > maxPayloadPartBytes) {
        throw new HistoricalChunkPayloadSizeException(
            sampledChunk.chunkKey(),
            "Historical payload point exceeds configured max payload part bytes for chunk "
                + sampledChunk.chunkKey());
      }
      if (!currentPart.isEmpty()
          && currentSizeBytes + point.serializedSizeBytes() > maxPayloadPartBytes) {
        parts.add(List.copyOf(currentPart));
        currentPart = new ArrayList<>();
        currentSizeBytes = maxMetadataBytes;
      }
      currentPart.add(point);
      currentSizeBytes += point.serializedSizeBytes();
    }
    if (!currentPart.isEmpty()) {
      parts.add(List.copyOf(currentPart));
    }
    return parts;
  }

  private static int worstCaseMetadataBytes(
      HistoricalChunkSampledResult sampledChunk, int maxPointsPerChunk, int maxObjectCount) {
    // partIndex and partCount are placed at their largest realistic values so the varint encoding
    // size we reserve here is always >= the encoding size the builder will actually emit.
    return HistoricalChunkPayloadPrototype.prefixBuilder(
            sampledChunk,
            Math.max(0, maxPointsPerChunk - 1),
            Math.max(1, maxPointsPerChunk),
            maxPointsPerChunk,
            maxObjectCount,
            true)
        .build()
        .getSerializedSize();
  }
}
