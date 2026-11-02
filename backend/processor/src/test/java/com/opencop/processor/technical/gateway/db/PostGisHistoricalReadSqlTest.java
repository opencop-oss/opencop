// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.processor.business.model.history.HistoricalReadCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostGisHistoricalReadSqlTest {

  @Test
  void winnersSql_withoutCursor_addsQualifiedOrderingOnly() {
    String sql = PostGisHistoricalReadSql.winnersSql(null);

    assertTrue(sql.contains("FROM winners"));
    assertTrue(sql.contains("ORDER BY winners.playback_at ASC"));
    assertFalse(sql.contains(":cursorPlaybackAt"));
  }

  @Test
  void winnersSql_withCursor_addsQualifiedKeysetPredicate() {
    String sql = PostGisHistoricalReadSql.winnersSql(cursor());

    assertTrue(sql.contains("winners.playback_at > :cursorPlaybackAt"));
    assertTrue(sql.contains("winners.object_id > :cursorObjectId"));
    assertTrue(sql.contains("winners.id > :cursorEventId"));
    assertTrue(sql.contains("ORDER BY winners.playback_at ASC"));
  }

  @Test
  void deltaSql_withCursor_addsQualifiedKeysetPredicate() {
    String sql = PostGisHistoricalReadSql.deltaSql(cursor());

    assertTrue(sql.contains("delta_rows.playback_at > :cursorPlaybackAt"));
    assertTrue(sql.contains("delta_rows.object_id > :cursorObjectId"));
    assertTrue(sql.contains("delta_rows.id > :cursorEventId"));
    assertTrue(sql.contains("ORDER BY delta_rows.playback_at ASC"));
  }

  @Test
  void streamStateDeltaSql_usesPostDeployWinnerShapeInsteadOfRawDeltaRows() {
    String sql = PostGisHistoricalReadSql.streamStateDeltaSql();

    assertTrue(sql.contains("WITH changed_objects AS MATERIALIZED"));
    assertTrue(sql.contains("SELECT DISTINCT"));
    assertTrue(sql.contains("features.source"));
    assertTrue(sql.contains("features.object_id"));
    assertTrue(sql.contains("JOIN LATERAL"));
    assertTrue(sql.contains("features.source = changed_objects.source"));
    assertTrue(sql.contains("features.object_id = changed_objects.object_id"));
    assertTrue(sql.contains("COALESCE(features.start_at, features.created_at) >= :from"));
    assertTrue(sql.contains("COALESCE(features.start_at, features.created_at) < :to"));
    assertTrue(sql.contains("JOIN geo.features payloads ON payloads.id = winners.id"));
    assertTrue(sql.contains("ORDER BY winners.playback_at ASC"));
    assertFalse(sql.contains("WITH delta_rows AS"));
  }

  private static HistoricalReadCursor cursor() {
    return new HistoricalReadCursor(
        Instant.parse("2026-04-06T10:02:00Z"),
        "vehicle-1",
        UUID.fromString("550e8400-e29b-41d4-a716-446655440010"));
  }
}
