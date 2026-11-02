// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.HistoricalSampledPlaybackPoint;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.SourceObjectKey;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkSampledResult;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class V1LatestInBucketSampler {
  private static final Comparator<HistoricalSampledPlaybackPoint> OUTPUT_ORDER =
      Comparator.comparing(HistoricalSampledPlaybackPoint::bucketStart)
          .thenComparing(HistoricalSampledPlaybackPoint::objectId)
          .thenComparing(HistoricalSampledPlaybackPoint::playbackAt)
          .thenComparing(HistoricalSampledPlaybackPoint::eventId);

  public HistoricalChunkSampledResult sampleChunk(
      HistoricalChunkPlanEntry chunk,
      HistoricalResolution resolution,
      Iterable<HistoricalSourceEvent> events) {
    ChunkAccumulator accumulator = newAccumulator(chunk, resolution);
    accumulator.accept(events);
    return accumulator.finish();
  }

  public ChunkAccumulator newAccumulator(
      HistoricalChunkPlanEntry chunk, HistoricalResolution resolution) {
    onAccumulatorCreated(chunk, resolution);
    return new ChunkAccumulator(chunk, resolution);
  }

  public static Instant alignBucketStart(Instant instant, HistoricalResolution resolution) {
    return switch (resolution) {
      case MINUTE_1 -> instant.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
      case HOUR_1 -> instant.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
      case DAY_1 -> instant.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
    };
  }

  private HistoricalSourceEvent pickWinner(
      HistoricalSourceEvent currentWinner, HistoricalSourceEvent candidate) {
    // Phase 1 determinism rule: the later playbackAt wins; ties fall back to the persisted row id.
    int byPlaybackAt = candidate.playbackAt().compareTo(currentWinner.playbackAt());
    if (byPlaybackAt != 0) {
      return byPlaybackAt > 0 ? candidate : currentWinner;
    }
    return candidate.eventId().compareTo(currentWinner.eventId()) > 0 ? candidate : currentWinner;
  }

  protected void onAccumulatorCreated(
      HistoricalChunkPlanEntry chunk, HistoricalResolution resolution) {}

  protected void onEventAccepted(HistoricalSourceEvent event) {}

  public final class ChunkAccumulator {
    private final HistoricalChunkPlanEntry chunk;
    private final HistoricalResolution resolution;
    private final Map<ObjectBucketKey, HistoricalSourceEvent> winners = new HashMap<>();
    private final Set<SourceObjectKey> objectKeys = new HashSet<>();

    private ChunkAccumulator(HistoricalChunkPlanEntry chunk, HistoricalResolution resolution) {
      this.chunk = chunk;
      this.resolution = resolution;
    }

    public void accept(HistoricalSourceEvent event) {
      onEventAccepted(event);
      Instant bucketStart = alignBucketStart(event.playbackAt(), resolution);
      SourceObjectKey sourceObjectKey = new SourceObjectKey(event.sourceId(), event.objectId());
      winners.merge(
          new ObjectBucketKey(sourceObjectKey, bucketStart),
          event,
          V1LatestInBucketSampler.this::pickWinner);
      objectKeys.add(sourceObjectKey);
    }

    public void accept(Iterable<HistoricalSourceEvent> events) {
      for (HistoricalSourceEvent event : events) {
        accept(event);
      }
    }

    public HistoricalChunkSampledResult finish() {
      List<HistoricalSampledPlaybackPoint> points =
          winners.entrySet().stream()
              .map(
                  entry ->
                      HistoricalSampledPlaybackPoint.fromEvent(
                          entry.getValue(), entry.getKey().bucketStart()))
              .sorted(OUTPUT_ORDER)
              .toList();
      return new HistoricalChunkSampledResult(
          chunk.chunkIndex(),
          chunk.chunkKey(),
          chunk.from(),
          chunk.to(),
          resolution,
          points.size(),
          objectKeys.size(),
          points);
    }
  }

  private record ObjectBucketKey(SourceObjectKey key, Instant bucketStart) {}
}
