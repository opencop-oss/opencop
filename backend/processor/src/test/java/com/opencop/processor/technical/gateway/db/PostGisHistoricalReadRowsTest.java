// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostGisHistoricalReadRowsTest {

  @Test
  void page_whenRowsExceedRequestedSize_returnsCursorFromLastIncludedRow() {
    var first = event("2026-04-06T10:00:00Z", "a");
    var second = event("2026-04-06T10:01:00Z", "b");
    var third = event("2026-04-06T10:02:00Z", "c");

    var page = PostGisHistoricalReadRows.page(List.of(first, second, third), 2);

    assertTrue(page.hasMore());
    assertEquals(List.of(first, second), page.rows());
    assertEquals(second.cursor(), page.nextCursor());
  }

  @Test
  void page_whenRowsFitRequestedSize_returnsNoCursor() {
    var first = event("2026-04-06T10:00:00Z", "a");

    var page = PostGisHistoricalReadRows.page(List.of(first), 2);

    assertFalse(page.hasMore());
    assertEquals(List.of(first), page.rows());
    assertNull(page.nextCursor());
  }

  @Test
  void pageOrder_sortsByPlaybackObjectAndEventId() {
    var eventA =
        event(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"), "2026-04-06T10:00:00Z", "b");
    var eventB =
        event(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"), "2026-04-06T10:00:00Z", "a");
    var eventC =
        event(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"), "2026-04-06T09:59:00Z", "z");
    var rows = new ArrayList<>(List.of(eventA, eventB, eventC));

    rows.sort(PostGisHistoricalReadRows.pageOrder());

    assertEquals(List.of(eventC, eventB, eventA), rows);
  }

  @Test
  void mapRow_mapsNullableTemporalColumns() throws Exception {
    UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    ResultSet rs = mock(ResultSet.class);
    when(rs.getObject("id", UUID.class)).thenReturn(eventId);
    when(rs.getObject("source", UUID.class)).thenReturn(sourceId);
    when(rs.getString("object_id")).thenReturn("vehicle-1");
    when(rs.getTimestamp("playback_at"))
        .thenReturn(Timestamp.from(Instant.parse("2026-04-06T10:02:00Z")));
    when(rs.getTimestamp("created_at"))
        .thenReturn(Timestamp.from(Instant.parse("2026-04-06T10:01:00Z")));
    when(rs.getTimestamp("start_at")).thenReturn(null);
    when(rs.getTimestamp("stale_at"))
        .thenReturn(Timestamp.from(Instant.parse("2026-04-06T10:05:00Z")));
    when(rs.getString("payload_json")).thenReturn("{\"type\":\"Feature\"}");

    var row = PostGisHistoricalReadRows.mapRow(rs);

    assertEquals(eventId, row.eventId());
    assertEquals(sourceId, row.sourceId());
    assertEquals("vehicle-1", row.objectId());
    assertEquals(Instant.parse("2026-04-06T10:02:00Z"), row.playbackAt());
    assertEquals(Instant.parse("2026-04-06T10:01:00Z"), row.createdAt());
    assertNull(row.startAt());
    assertEquals(Instant.parse("2026-04-06T10:05:00Z"), row.staleAt());
    assertEquals("{\"type\":\"Feature\"}", row.payloadJson());
  }

  private static HistoricalSourceEvent event(String playbackAt, String objectId) {
    return event(UUID.randomUUID(), playbackAt, objectId);
  }

  private static HistoricalSourceEvent event(UUID eventId, String playbackAt, String objectId) {
    return new HistoricalSourceEvent(
        eventId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440020"),
        objectId,
        Instant.parse(playbackAt),
        Instant.parse(playbackAt),
        null,
        null,
        "{}");
  }
}
