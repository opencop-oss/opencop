// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostGisServiceTest {

  @Test
  void helperRecordsFor_keepsLatestPlaybackAtPerSourceAndObjectId() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440300");
    UUID olderEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440301");
    UUID newerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440302");

    List<PostGisService.HelperRecord> helperRecords =
        PostGisService.helperRecordsFor(
            List.of(
                featureRecord(olderEventId, sourceId, "obj-1", "2026-04-16T14:00:00Z", null),
                featureRecord(newerEventId, sourceId, "obj-1", "2026-04-16T14:01:00Z", null)));

    assertEquals(1, helperRecords.size());
    assertEquals(newerEventId, helperRecords.getFirst().eventId());
    assertEquals(utc("2026-04-16T14:01:00Z"), helperRecords.getFirst().playbackAt());
  }

  @Test
  void helperRecordsFor_breaksPlaybackTieByLatestEventId() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440310");
    UUID lowerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440311");
    UUID higherEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440312");

    List<PostGisService.HelperRecord> helperRecords =
        PostGisService.helperRecordsFor(
            List.of(
                featureRecord(lowerEventId, sourceId, "obj-tie", "2026-04-16T14:02:00Z", null),
                featureRecord(higherEventId, sourceId, "obj-tie", "2026-04-16T14:02:00Z", null)));

    assertEquals(1, helperRecords.size());
    assertEquals(higherEventId, helperRecords.getFirst().eventId());
  }

  @Test
  void helperRecordsFor_skipsNullObjectIdAndPreservesIndependentKeys() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440320");
    UUID firstEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440321");
    UUID secondEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440322");
    UUID nullObjectEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440323");

    List<PostGisService.HelperRecord> helperRecords =
        PostGisService.helperRecordsFor(
            List.of(
                featureRecord(firstEventId, sourceId, "obj-a", "2026-04-16T14:03:00Z", null),
                featureRecord(secondEventId, sourceId, "obj-b", "2026-04-16T14:04:00Z", null),
                featureRecord(nullObjectEventId, sourceId, null, "2026-04-16T14:05:00Z", null)));

    assertEquals(2, helperRecords.size());
    assertEquals(List.of("obj-a", "obj-b"), helperRecords.stream().map(r -> r.objectId()).toList());
  }

  @Test
  void helperRecordsFor_returnsCanonicalLockOrderAcrossSourcesAndObjectIds() {
    UUID laterSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440340");
    UUID earlierSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440339");

    List<PostGisService.HelperRecord> helperRecords =
        PostGisService.helperRecordsFor(
            List.of(
                featureRecord(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440343"),
                    laterSourceId,
                    "obj-z",
                    "2026-04-16T14:06:00Z",
                    null),
                featureRecord(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440342"),
                    earlierSourceId,
                    "obj-b",
                    "2026-04-16T14:06:00Z",
                    null),
                featureRecord(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440341"),
                    earlierSourceId,
                    "obj-a",
                    "2026-04-16T14:06:00Z",
                    null)));

    assertEquals(3, helperRecords.size());
    assertEquals(earlierSourceId, helperRecords.get(0).source());
    assertEquals("obj-a", helperRecords.get(0).objectId());
    assertEquals(earlierSourceId, helperRecords.get(1).source());
    assertEquals("obj-b", helperRecords.get(1).objectId());
    assertEquals(laterSourceId, helperRecords.get(2).source());
    assertEquals("obj-z", helperRecords.get(2).objectId());
  }

  @Test
  void helperRecordsFor_preservesNullPlaybackAndStaleValuesWhenOnlyCandidateExists() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440330");
    UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440331");

    List<PostGisService.HelperRecord> helperRecords =
        PostGisService.helperRecordsFor(
            List.of(featureRecord(eventId, sourceId, "obj-null", null, null)));

    assertEquals(1, helperRecords.size());
    assertNull(helperRecords.getFirst().playbackAt());
    assertNull(helperRecords.getFirst().staleAt());
  }

  @Test
  void helperRecordExecutionGroupsFor_chunksOrderedHelperRecordsIntoFixedGroupsOfOneHundred() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440350");

    List<PostGisFeatureRecord> featureRecords =
        java.util.stream.IntStream.range(0, 205)
            .mapToObj(
                index ->
                    featureRecord(
                        UUID.fromString(
                            "550e8400-e29b-41d4-a716-%012d".formatted(446655440400L + index)),
                        sourceId,
                        "obj-%03d".formatted(index),
                        "2026-04-16T14:06:00Z",
                        null))
            .toList();

    List<List<PostGisService.HelperRecord>> executionGroups =
        PostGisService.helperRecordExecutionGroupsFor(featureRecords);

    assertEquals(3, executionGroups.size());
    assertEquals(100, executionGroups.get(0).size());
    assertEquals(100, executionGroups.get(1).size());
    assertEquals(5, executionGroups.get(2).size());
    assertEquals("obj-000", executionGroups.get(0).getFirst().objectId());
    assertEquals("obj-204", executionGroups.get(2).getLast().objectId());
  }

  private static PostGisFeatureRecord featureRecord(
      UUID eventId, UUID sourceId, String objectId, String startAt, String staleAt) {
    return new PostGisFeatureRecord(
        eventId,
        "TRACK",
        sourceId,
        null,
        objectId,
        startAt == null ? null : Instant.parse(startAt),
        staleAt == null ? null : Instant.parse(staleAt),
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        "{\"type\":\"Feature\"}");
  }

  private static OffsetDateTime utc(String value) {
    return OffsetDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
  }
}
