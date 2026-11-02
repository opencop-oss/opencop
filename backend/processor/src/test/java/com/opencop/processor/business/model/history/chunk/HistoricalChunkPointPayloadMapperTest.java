// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.CodedOutputStream;
import com.opencop.processor.business.model.history.HistoricalSampledPlaybackPoint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalChunkPointPayloadMapperTest {

  private static final int ELEMENTS_FIELD_NUMBER = 9;

  private final HistoricalChunkPointPayloadMapper mapper =
      new HistoricalChunkPointPayloadMapper(new ObjectMapper());

  @Test
  void map_parsesGeoJsonAndBuildsElementPayload() {
    var point =
        point(
            "550e8400-e29b-41d4-a716-446655440101",
            "550e8400-e29b-41d4-a716-446655440201",
            "obj-1",
            "2026-04-06T10:02:00Z",
            "2026-04-06T10:02:03Z",
            -73.5,
            45.5);

    var payload = mapper.map(point);

    assertEquals(point.sourceId(), payload.sourceObjectKey().source());
    assertEquals("obj-1", payload.sourceObjectKey().objectId());
    assertEquals("obj-1", payload.objectId());
    ElementMessage message = payload.message();
    assertEquals("obj-1", message.getObjectId());
    assertEquals("a-f-G-U-C", message.getType());
    assertEquals("Object obj-1", message.getDisplayName());
    assertEquals(Instant.parse("2026-04-06T10:02:03Z").toEpochMilli(), message.getTime());
    assertEquals(List.of(-73.5, 45.5), message.getPositionsList());
    assertEquals(12.5, message.getValuesMap().get("speed").getDouble());
    assertEquals("alpha", message.getValuesMap().get("remarks").getString());
  }

  @Test
  void map_calculatesRepeatedElementWireSize() {
    var payload =
        mapper.map(
            point(
                "550e8400-e29b-41d4-a716-446655440102",
                "550e8400-e29b-41d4-a716-446655440202",
                "obj-2",
                "2026-04-06T10:02:00Z",
                "2026-04-06T10:02:03Z",
                -73.6,
                45.6));

    int messageSize = payload.message().getSerializedSize();
    int expectedSize =
        CodedOutputStream.computeTagSize(ELEMENTS_FIELD_NUMBER)
            + CodedOutputStream.computeUInt32SizeNoTag(messageSize)
            + messageSize;
    assertEquals(expectedSize, payload.serializedSizeBytes());
  }

  @Test
  void map_whenPayloadJsonIsInvalid_throwsEventSpecificException() {
    var point =
        new HistoricalSampledPlaybackPoint(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
            "obj-3",
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T10:02:03Z"),
            "{not-json");

    var thrown = assertThrows(IllegalStateException.class, () -> mapper.map(point));

    assertTrue(thrown.getMessage().contains("Failed to parse sampled payload JSON"));
    assertTrue(thrown.getMessage().contains(point.eventId().toString()));
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
