// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.HistoricalSampledPlaybackPoint;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkSampledResult;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkProfile;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkResolution;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalChunkPayloadBuilderTest {

  private final HistoricalChunkPayloadBuilder builder =
      HistoricalChunkPayloadBuilder.create(
          new ObjectMapper(), new HistoricalCacheIdentity(new ObjectMapper()), 131_072, 524_288);

  @Test
  void buildPayload_preservesChunkMetadataCountsAndPointIdentity() throws Exception {
    var sampledChunk =
        new HistoricalChunkSampledResult(
            3,
            "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:02:00Z",
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T10:03:00Z"),
            HistoricalResolution.MINUTE_1,
            2,
            2,
            List.of(
                point(
                    "550e8400-e29b-41d4-a716-446655440101",
                    "550e8400-e29b-41d4-a716-446655440201",
                    "obj-1",
                    "2026-04-06T10:02:00Z",
                    "2026-04-06T10:02:03Z",
                    -73.5,
                    45.5),
                point(
                    "550e8400-e29b-41d4-a716-446655440102",
                    "550e8400-e29b-41d4-a716-446655440202",
                    "obj-2",
                    "2026-04-06T10:02:00Z",
                    "2026-04-06T10:02:18Z",
                    -73.6,
                    45.6)));

    var result = builder.buildPayload(sampledChunk, "opencop:history:payload:test-chunk");
    assertEquals(1, result.parts().size());
    var part = result.parts().getFirst();
    HistoricalChunkPayload parsed = HistoricalChunkPayload.parseFrom(part.serializedPayload());

    assertEquals(sampledChunk.chunkKey(), parsed.getChunkKey());
    assertEquals(sampledChunk.chunkIndex(), parsed.getChunkIndex());
    assertEquals(sampledChunk.from().toEpochMilli(), parsed.getFromEpochMillis());
    assertEquals(sampledChunk.to().toEpochMilli(), parsed.getToEpochMillis());
    assertEquals(
        HistoricalChunkResolution.HISTORICAL_CHUNK_RESOLUTION_MINUTE_1, parsed.getResolution());
    assertEquals(HistoricalChunkProfile.HISTORICAL_CHUNK_PROFILE_DETAIL, parsed.getProfile());
    assertEquals(sampledChunk.pointCount(), parsed.getPointCount());
    assertEquals(sampledChunk.objectCount(), parsed.getObjectCount());
    assertEquals(2, parsed.getElementsCount());
    ElementMessage firstElement = parsed.getElements(0);
    assertEquals("obj-1", firstElement.getObjectId());
    assertEquals("a-f-G-U-C", firstElement.getType());
    assertEquals("Object obj-1", firstElement.getDisplayName());
    assertEquals(Instant.parse("2026-04-06T10:02:03Z").toEpochMilli(), firstElement.getTime());
    assertEquals(Instant.parse("2026-04-06T10:02:03Z").toEpochMilli(), firstElement.getStart());
    assertEquals(List.of(-73.5, 45.5), firstElement.getPositionsList());
    assertEquals(12.5, firstElement.getValuesMap().get("speed").getDouble());
    assertEquals("alpha", firstElement.getValuesMap().get("remarks").getString());
    assertEquals(part.payload(), parsed);
    assertEquals(part.serializedPayload().length, part.serializedSizeBytes());
    assertEquals("opencop:history:payload:test-chunk", part.payloadKey());
    assertEquals(0, parsed.getPartIndex());
    assertEquals(1, parsed.getPartCount());
    assertTrue(parsed.getFinalForChunk());
  }

  @Test
  void buildPayload_isDeterministicForRepeatedIdenticalInput() {
    var sampledChunk =
        new HistoricalChunkSampledResult(
            0,
            "opencop:history:chunk:SOURCE:test:HOUR_1:v1:2026-04-06T10:00:00Z",
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            1,
            1,
            List.of(
                point(
                    "550e8400-e29b-41d4-a716-446655440103",
                    "550e8400-e29b-41d4-a716-446655440203",
                    "obj-9",
                    "2026-04-06T10:00:00Z",
                    "2026-04-06T10:15:00Z",
                    -73.7,
                    45.7)));

    var first = builder.buildPayload(sampledChunk, "opencop:history:payload:test-hour");
    var second = builder.buildPayload(sampledChunk, "opencop:history:payload:test-hour");

    assertEquals(1, first.parts().size());
    assertEquals(1, second.parts().size());
    assertArrayEquals(
        first.parts().getFirst().serializedPayload(),
        second.parts().getFirst().serializedPayload());
    assertEquals(first.parts().getFirst().payload(), second.parts().getFirst().payload());
    assertEquals(
        first.parts().getFirst().serializedSizeBytes(),
        second.parts().getFirst().serializedSizeBytes());
  }

  @Test
  void buildPayload_splitsOversizedChunkIntoDeterministicOrderedParts() {
    HistoricalChunkPayloadBuilder splittingBuilder =
        HistoricalChunkPayloadBuilder.create(
            new ObjectMapper(), new HistoricalCacheIdentity(new ObjectMapper()), 500, 524_288);
    var sampledChunk =
        new HistoricalChunkSampledResult(
            5,
            "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:05:00Z",
            Instant.parse("2026-04-06T10:05:00Z"),
            Instant.parse("2026-04-06T10:06:00Z"),
            HistoricalResolution.MINUTE_1,
            3,
            3,
            List.of(
                point(
                    "550e8400-e29b-41d4-a716-446655440105",
                    "550e8400-e29b-41d4-a716-446655440205",
                    "obj-5",
                    "2026-04-06T10:05:00Z",
                    "2026-04-06T10:05:05Z",
                    -73.1,
                    45.1),
                point(
                    "550e8400-e29b-41d4-a716-446655440106",
                    "550e8400-e29b-41d4-a716-446655440206",
                    "obj-6",
                    "2026-04-06T10:05:00Z",
                    "2026-04-06T10:05:15Z",
                    -73.2,
                    45.2),
                point(
                    "550e8400-e29b-41d4-a716-446655440107",
                    "550e8400-e29b-41d4-a716-446655440207",
                    "obj-7",
                    "2026-04-06T10:05:00Z",
                    "2026-04-06T10:05:25Z",
                    -73.3,
                    45.3)));

    var result = splittingBuilder.buildPayload(sampledChunk, "opencop:history:payload:split");

    assertTrue(result.parts().size() > 1);
    assertEquals("opencop:history:payload:split", result.parts().get(0).payloadKey());
    assertEquals("opencop:history:payload:split:part:1", result.parts().get(1).payloadKey());
    assertEquals(0, result.parts().get(0).partIndex());
    assertEquals(result.parts().size(), result.parts().get(0).partCount());
    assertTrue(result.parts().stream().allMatch(part -> part.serializedSizeBytes() <= 500));
    assertTrue(
        result.parts().stream()
            .allMatch(part -> part.payload().getObjectCount() == part.payload().getPointCount()));
    assertTrue(result.parts().getLast().finalForChunk());
  }

  @Test
  void buildPayload_failsWhenSinglePointExceedsFragmentThreshold() {
    HistoricalChunkPayloadBuilder strictBuilder =
        HistoricalChunkPayloadBuilder.create(
            new ObjectMapper(), new HistoricalCacheIdentity(new ObjectMapper()), 120, 524_288);
    var sampledChunk =
        new HistoricalChunkSampledResult(
            1,
            "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:01:00Z",
            Instant.parse("2026-04-06T10:01:00Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            HistoricalResolution.MINUTE_1,
            1,
            1,
            List.of(
                point(
                    "550e8400-e29b-41d4-a716-446655440108",
                    "550e8400-e29b-41d4-a716-446655440208",
                    "obj-8",
                    "2026-04-06T10:01:00Z",
                    "2026-04-06T10:01:05Z",
                    -73.8,
                    45.8)));

    var thrown =
        assertThrows(
            IllegalStateException.class,
            () -> strictBuilder.buildPayload(sampledChunk, "opencop:history:payload:too-large"));

    assertTrue(thrown.getMessage().contains("max payload part bytes"));
  }

  /**
   * Regression guard: building a payload from N distinct objectIds under a single source must
   * produce objectCount == N, identical to the pre-change behaviour. The source-aware internal
   * grouping must not inflate or collapse the count for single-source input.
   */
  @Test
  void distinctObjectCountUnchangedForSingleSource() {
    // Three distinct objectIds, all under the same sourceId.
    String sharedSource = "550e8400-e29b-41d4-a716-446655440aaa";
    var sampledChunk =
        new HistoricalChunkSampledResult(
            0,
            "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:10:00Z",
            Instant.parse("2026-04-06T10:10:00Z"),
            Instant.parse("2026-04-06T10:11:00Z"),
            HistoricalResolution.MINUTE_1,
            3,
            3,
            List.of(
                point(
                    "550e8400-e29b-41d4-a716-446655440301",
                    sharedSource,
                    "obj-x",
                    "2026-04-06T10:10:00Z",
                    "2026-04-06T10:10:05Z",
                    -74.0,
                    43.0),
                point(
                    "550e8400-e29b-41d4-a716-446655440302",
                    sharedSource,
                    "obj-y",
                    "2026-04-06T10:10:00Z",
                    "2026-04-06T10:10:15Z",
                    -74.1,
                    43.1),
                point(
                    "550e8400-e29b-41d4-a716-446655440303",
                    sharedSource,
                    "obj-z",
                    "2026-04-06T10:10:00Z",
                    "2026-04-06T10:10:25Z",
                    -74.2,
                    43.2)));

    var result = builder.buildPayload(sampledChunk, "opencop:history:payload:distinct-count");

    assertEquals(1, result.parts().size());
    var part = result.parts().getFirst();
    // Three distinct (source, objectId) pairs under a single source → objectCount must equal 3.
    assertEquals(3, part.payload().getObjectCount());
  }

  private static HistoricalSampledPlaybackPoint point(
      String eventId,
      String sourceId,
      String objectId,
      String bucketStart,
      String playbackAt,
      double lon,
      double lat) {
    return new HistoricalSampledPlaybackPoint(
        UUID.fromString(eventId),
        UUID.fromString(sourceId),
        objectId,
        Instant.parse(bucketStart),
        Instant.parse(playbackAt),
        pointFeatureJson(eventId, sourceId, objectId, playbackAt, lon, lat));
  }

  private static String pointFeatureJson(
      String eventId, String sourceId, String objectId, String playbackAt, double lon, double lat) {
    long playbackEpochMillis = Instant.parse(playbackAt).toEpochMilli();
    return """
        {
          "type": "Feature",
          "geometry": {
            "type": "Point",
            "coordinates": [%s, %s]
          },
          "properties": {
            "opencop": {
              "id": "%s",
              "objectId": "%s",
              "source": "%s",
              "displayName": "Object %s",
              "timestampz": {
                "time": %s,
                "start": %s,
                "stale": %s
              },
              "element": {
                "type": "a-f-G-U-C",
                "speed": 12.5,
                "remarks": "alpha"
              }
            }
          }
        }
        """
        .formatted(
            lon,
            lat,
            eventId,
            objectId,
            sourceId,
            objectId,
            playbackEpochMillis,
            playbackEpochMillis,
            playbackEpochMillis + 60_000L);
  }
}
