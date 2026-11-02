// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkProfile;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkResolution;

/**
 * Shared protobuf prefix construction for {@link HistoricalChunkPayload}. The partitioner needs to
 * predict the exact prefix the builder will emit so its size estimate stays in sync; all callers go
 * through this helper so the two cannot drift.
 */
final class HistoricalChunkPayloadPrototype {

  private HistoricalChunkPayloadPrototype() {}

  static HistoricalChunkPayload.Builder prefixBuilder(
      HistoricalChunkSampledResult sampledChunk,
      int partIndex,
      int partCount,
      int pointCount,
      int objectCount,
      boolean finalForChunk) {
    return HistoricalChunkPayload.newBuilder()
        .setChunkKey(sampledChunk.chunkKey())
        .setChunkIndex(sampledChunk.chunkIndex())
        .setFromEpochMillis(sampledChunk.from().toEpochMilli())
        .setToEpochMillis(sampledChunk.to().toEpochMilli())
        .setResolution(mapResolution(sampledChunk.resolution()))
        .setProfile(HistoricalChunkProfile.HISTORICAL_CHUNK_PROFILE_DETAIL)
        .setPointCount(pointCount)
        .setObjectCount(objectCount)
        .setPartIndex(partIndex)
        .setPartCount(partCount)
        .setFinalForChunk(finalForChunk);
  }

  private static HistoricalChunkResolution mapResolution(HistoricalResolution resolution) {
    return switch (resolution) {
      case MINUTE_1 -> HistoricalChunkResolution.HISTORICAL_CHUNK_RESOLUTION_MINUTE_1;
      case HOUR_1 -> HistoricalChunkResolution.HISTORICAL_CHUNK_RESOLUTION_HOUR_1;
      case DAY_1 -> HistoricalChunkResolution.HISTORICAL_CHUNK_RESOLUTION_DAY_1;
    };
  }
}
