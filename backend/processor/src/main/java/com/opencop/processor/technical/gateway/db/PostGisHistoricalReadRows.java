// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalReadPage;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class PostGisHistoricalReadRows {

  private PostGisHistoricalReadRows() {}

  static HistoricalReadPage page(List<HistoricalSourceEvent> rows, int requestedPageSize) {
    boolean hasMore = rows.size() > requestedPageSize;
    List<HistoricalSourceEvent> pageRows = hasMore ? rows.subList(0, requestedPageSize) : rows;
    HistoricalReadCursor nextCursor =
        hasMore && !pageRows.isEmpty() ? pageRows.getLast().cursor() : null;
    return new HistoricalReadPage(pageRows, nextCursor, hasMore);
  }

  static Comparator<HistoricalSourceEvent> pageOrder() {
    return Comparator.comparing(HistoricalSourceEvent::playbackAt)
        .thenComparing(HistoricalSourceEvent::objectId)
        .thenComparing(HistoricalSourceEvent::eventId);
  }

  static HistoricalSourceEvent mapRow(ResultSet rs) throws SQLException {
    UUID eventId = rs.getObject("id", UUID.class);
    UUID sourceId = rs.getObject("source", UUID.class);
    String objectId = rs.getString("object_id");
    Instant playbackAt = rs.getTimestamp("playback_at").toInstant();
    Instant createdAt = rs.getTimestamp("created_at").toInstant();
    Timestamp startAtValue = rs.getTimestamp("start_at");
    Instant startAt = startAtValue == null ? null : startAtValue.toInstant();
    Timestamp staleAtValue = rs.getTimestamp("stale_at");
    Instant staleAt = staleAtValue == null ? null : staleAtValue.toInstant();
    String payloadJson = rs.getString("payload_json");
    return new HistoricalSourceEvent(
        eventId, sourceId, objectId, playbackAt, createdAt, startAt, staleAt, payloadJson);
  }
}
