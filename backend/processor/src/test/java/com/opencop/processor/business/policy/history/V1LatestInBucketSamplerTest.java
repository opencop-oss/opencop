// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class V1LatestInBucketSamplerTest {

  private final V1LatestInBucketSampler sampler = new V1LatestInBucketSampler();

  @Test
  void sampleChunk_choosesLatestPlaybackAtWithinObjectBucket() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:03:00Z");
    var earlier = event("obj-1", "2026-04-06T10:02:05Z", "550e8400-e29b-41d4-a716-446655440101");
    var later = event("obj-1", "2026-04-06T10:02:55Z", "550e8400-e29b-41d4-a716-446655440102");

    var result = sampler.sampleChunk(chunk, HistoricalResolution.MINUTE_1, List.of(earlier, later));

    assertEquals(1, result.pointCount());
    assertEquals(1, result.objectCount());
    assertEquals(later.eventId(), result.points().getFirst().eventId());
    assertEquals(later.playbackAt(), result.points().getFirst().playbackAt());
  }

  @Test
  void sampleChunk_whenPlaybackMatches_usesLargestEventIdAsTieBreaker() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:03:00Z");
    var lowerId = event("obj-1", "2026-04-06T10:02:10Z", "550e8400-e29b-41d4-a716-446655440101");
    var higherId = event("obj-1", "2026-04-06T10:02:10Z", "550e8400-e29b-41d4-a716-446655440201");

    var result =
        sampler.sampleChunk(chunk, HistoricalResolution.MINUTE_1, List.of(higherId, lowerId));

    assertEquals(1, result.pointCount());
    assertEquals(higherId.eventId(), result.points().getFirst().eventId());
  }

  @Test
  void sampleChunk_samplesDifferentObjectsIndependently() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:03:00Z");
    var obj1Earlier =
        event("obj-1", "2026-04-06T10:02:05Z", "550e8400-e29b-41d4-a716-446655440101");
    var obj1Later = event("obj-1", "2026-04-06T10:02:50Z", "550e8400-e29b-41d4-a716-446655440102");
    var obj2Only = event("obj-2", "2026-04-06T10:02:15Z", "550e8400-e29b-41d4-a716-446655440103");

    var result =
        sampler.sampleChunk(
            chunk, HistoricalResolution.MINUTE_1, List.of(obj2Only, obj1Earlier, obj1Later));

    assertEquals(2, result.pointCount());
    assertEquals(2, result.objectCount());
    assertEquals(
        List.of("obj-1", "obj-2"),
        result.points().stream().map(point -> point.objectId()).toList());
    assertEquals(obj1Later.eventId(), result.points().get(0).eventId());
    assertEquals(obj2Only.eventId(), result.points().get(1).eventId());
  }

  @Test
  void alignBucketStart_usesUtcAlignedBoundaries() {
    Instant instant = Instant.parse("2026-04-06T10:02:59.999Z");

    assertEquals(
        Instant.parse("2026-04-06T10:02:00Z"),
        V1LatestInBucketSampler.alignBucketStart(instant, HistoricalResolution.MINUTE_1));
    assertEquals(
        Instant.parse("2026-04-06T10:00:00Z"),
        V1LatestInBucketSampler.alignBucketStart(instant, HistoricalResolution.HOUR_1));
    assertEquals(
        Instant.parse("2026-04-06T00:00:00Z"),
        V1LatestInBucketSampler.alignBucketStart(instant, HistoricalResolution.DAY_1));
  }

  @Test
  void sampleChunk_isDeterministicForRepeatedInputInDifferentIterationOrders() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:03:00Z");
    var obj1First = event("obj-1", "2026-04-06T10:02:05Z", "550e8400-e29b-41d4-a716-446655440101");
    var obj1Winner = event("obj-1", "2026-04-06T10:02:45Z", "550e8400-e29b-41d4-a716-446655440201");
    var obj2Winner = event("obj-2", "2026-04-06T10:02:20Z", "550e8400-e29b-41d4-a716-446655440301");

    var forward =
        sampler.sampleChunk(
            chunk, HistoricalResolution.MINUTE_1, List.of(obj1First, obj1Winner, obj2Winner));
    var reversed =
        sampler.sampleChunk(
            chunk, HistoricalResolution.MINUTE_1, List.of(obj2Winner, obj1Winner, obj1First));

    assertEquals(forward, reversed);
  }

  @Test
  void sampleChunk_prefersNewerInChunkEventOverCarriedForwardOlderEvent() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:03:00Z");
    var carriedForward =
        event("obj-1", "2026-04-06T10:01:40Z", "550e8400-e29b-41d4-a716-446655440111");
    var newerInChunk =
        event("obj-1", "2026-04-06T10:02:35Z", "550e8400-e29b-41d4-a716-446655440222");

    var result =
        sampler.sampleChunk(
            chunk, HistoricalResolution.MINUTE_1, List.of(carriedForward, newerInChunk));

    assertEquals(2, result.pointCount());
    assertEquals(1, result.objectCount());
    assertEquals(carriedForward.eventId(), result.points().get(0).eventId());
    assertEquals(newerInChunk.eventId(), result.points().get(1).eventId());
  }

  @Test
  void sampleChunk_doesNotInterpolateOrGapFillMissingBuckets() {
    var chunk = chunk("2026-04-06T10:00:00Z", "2026-04-06T12:00:00Z");
    var onlyEvent = event("obj-1", "2026-04-06T11:15:00Z", "550e8400-e29b-41d4-a716-446655440101");

    var result = sampler.sampleChunk(chunk, HistoricalResolution.HOUR_1, List.of(onlyEvent));

    assertEquals(1, result.pointCount());
    assertEquals(1, result.objectCount());
    assertEquals(Instant.parse("2026-04-06T11:00:00Z"), result.points().getFirst().bucketStart());
  }

  /**
   * Golden-fixture test proving the sampler produces a deterministic, correct output for a
   * single-source input. The fixture has three objects over two minute buckets. The expected output
   * is hand-derived from the input by applying the latest-wins rule per (source, objectId, bucket):
   *
   * <ul>
   *   <li>obj-A in 10:02 bucket → only one event → eventId 101
   *   <li>obj-B in 10:02 bucket → two events, playbackAt 10:02:10 wins over 10:02:05 → eventId 103
   *   <li>obj-C in 10:03 bucket → only one event → eventId 104
   * </ul>
   *
   * Output order is (bucketStart asc, objectId asc, playbackAt asc, eventId asc), giving: obj-A at
   * 10:02, obj-B at 10:02, obj-C at 10:03.
   */
  @Test
  void samplerOutputByteEquivalentForSingleSource() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:04:00Z");
    // obj-A: single event in the 10:02 minute bucket
    var objA = event("obj-A", "2026-04-06T10:02:20Z", "550e8400-e29b-41d4-a716-446655440101");
    // obj-B: two events in the 10:02 minute bucket — the later playbackAt (10:02:10) wins
    var objBEarlier =
        event("obj-B", "2026-04-06T10:02:05Z", "550e8400-e29b-41d4-a716-446655440102");
    var objBLater = event("obj-B", "2026-04-06T10:02:10Z", "550e8400-e29b-41d4-a716-446655440103");
    // obj-C: single event in the 10:03 minute bucket
    var objC = event("obj-C", "2026-04-06T10:03:30Z", "550e8400-e29b-41d4-a716-446655440104");

    var result =
        sampler.sampleChunk(
            chunk, HistoricalResolution.MINUTE_1, List.of(objA, objBEarlier, objBLater, objC));

    // 3 distinct objects, 3 output points (one winner per (source, objectId, bucket))
    assertEquals(3, result.objectCount());
    assertEquals(3, result.pointCount());

    // Verify the output matches the hand-derived expected sequence.
    // Format: "objectId@bucketStart/eventId" to capture all identity fields.
    List<String> actualTokens =
        result.points().stream()
            .map(p -> p.objectId() + "@" + p.bucketStart() + "/" + p.eventId())
            .collect(Collectors.toList());

    List<String> expectedTokens =
        List.of(
            "obj-A@2026-04-06T10:02:00Z/550e8400-e29b-41d4-a716-446655440101",
            "obj-B@2026-04-06T10:02:00Z/550e8400-e29b-41d4-a716-446655440103",
            "obj-C@2026-04-06T10:03:00Z/550e8400-e29b-41d4-a716-446655440104");

    assertEquals(expectedTokens, actualTokens);
  }

  /**
   * Two sources that each have an object with the same objectId string are treated as distinct
   * entities because the internal key is (source, objectId). The sampler must produce one sample
   * point per source, not collapse them into one.
   */
  @Test
  void twoSourcesWithSharedObjectIdProduceTwoDistinctSamplePoints() {
    var chunk = chunk("2026-04-06T10:02:00Z", "2026-04-06T10:03:00Z");
    // Both sources use objectId "shared" in the same minute bucket.
    var srcA = "550e8400-e29b-41d4-a716-446655440aaa";
    var srcB = "550e8400-e29b-41d4-a716-446655440bbb";
    var eventFromSourceA =
        eventWithSource(
            "shared", "2026-04-06T10:02:15Z", "550e8400-e29b-41d4-a716-446655440201", srcA);
    var eventFromSourceB =
        eventWithSource(
            "shared", "2026-04-06T10:02:40Z", "550e8400-e29b-41d4-a716-446655440202", srcB);

    var result =
        sampler.sampleChunk(
            chunk, HistoricalResolution.MINUTE_1, List.of(eventFromSourceA, eventFromSourceB));

    // Source-aware keying means two distinct (source, objectId) pairs → two output points.
    assertEquals(2, result.pointCount());
    assertEquals(2, result.objectCount());
  }

  private static HistoricalChunkPlanEntry chunk(String from, String to) {
    return new HistoricalChunkPlanEntry(
        0,
        "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:%s".formatted(from),
        null,
        null,
        null,
        Instant.parse(from),
        Instant.parse(to));
  }

  private static HistoricalSourceEvent event(String objectId, String playbackAt, String eventId) {
    return eventWithSource(objectId, playbackAt, eventId, "550e8400-e29b-41d4-a716-446655440999");
  }

  private static HistoricalSourceEvent eventWithSource(
      String objectId, String playbackAt, String eventId, String sourceId) {
    Instant playback = Instant.parse(playbackAt);
    return new HistoricalSourceEvent(
        UUID.fromString(eventId),
        UUID.fromString(sourceId),
        objectId,
        playback,
        playback,
        playback,
        playback.plusSeconds(60),
        "{\"objectId\":\"%s\",\"eventId\":\"%s\"}".formatted(objectId, eventId));
  }
}
