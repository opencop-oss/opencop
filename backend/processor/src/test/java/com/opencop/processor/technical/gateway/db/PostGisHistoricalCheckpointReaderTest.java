// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostGisHistoricalCheckpointReaderTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final PostGisHistoricalDeltaReader deltaReader =
      new PostGisHistoricalDeltaReader(jdbc, Duration.ofSeconds(9));
  private final PostGisHistoricalCheckpointReader reader =
      new PostGisHistoricalCheckpointReader(jdbc, deltaReader, Duration.ofSeconds(9));

  @Test
  void read_whenCheckpointHasNoRows_returnsEmptyPageWithoutDeltaReplay() {
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of());

    var page = reader.read(context(), chunk(), 10);

    assertEquals(List.of(), page.rows());
    assertFalse(page.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(1))
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertEquals(PostGisHistoricalReadSql.CHECKPOINT_SQL, sqlCaptor.getValue());
    assertEquals(context().singleSourceId(), paramsCaptor.getValue().get("sourceId"));
    assertEquals(
        HistoricalResolution.MINUTE_1.name(), paramsCaptor.getValue().get("checkpointResolution"));
    assertEquals(Timestamp.from(chunk().from()), paramsCaptor.getValue().get("checkpointAt"));
  }

  @Test
  void read_appliesQueryTimeoutToCheckpointAndReplayStatements() throws Exception {
    var statements =
        NamedParameterJdbcExecuteStub.stubExecuteRows(
            jdbc, List.of(event("checkpoint", "2026-04-06T10:01:00Z", null)), List.of());

    reader.read(context(), chunk(), 10);

    verify(statements.getFirst()).setQueryTimeout(9);
    verify(statements.get(1)).setQueryTimeout(9);
    verify(jdbc, never()).getJdbcTemplate();
  }

  @Test
  void read_whenCheckpointRows_requirePageTwoUsesCursorOverMaterializedRows() {
    var first = event("first", "2026-04-06T10:01:00Z", null);
    var second = event("second", "2026-04-06T10:01:30Z", null);
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(first, second), List.of(), List.of(first, second), List.of());

    var firstPage = reader.read(context(), chunk(), 1, null);
    var secondPage = reader.read(context(), chunk(), 1, firstPage.nextCursor());

    assertEquals(List.of(first), firstPage.rows());
    assertTrue(firstPage.hasMore());
    assertEquals(List.of(second), secondPage.rows());
    assertFalse(secondPage.hasMore());
    assertNull(secondPage.nextCursor());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(4))
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertEquals(PostGisHistoricalReadSql.CHECKPOINT_SQL, sqlCaptor.getAllValues().get(0));
    assertTrue(sqlCaptor.getAllValues().get(1).contains("WITH delta_rows AS"));
    assertEquals(PostGisHistoricalReadSql.CHECKPOINT_SQL, sqlCaptor.getAllValues().get(2));
    assertFalse(sqlCaptor.getAllValues().get(3).contains("SELECT DISTINCT ON (source, object_id)"));
    assertEquals(context().singleSourceId(), paramsCaptor.getAllValues().get(0).get("sourceId"));
    assertEquals(context().singleSourceId(), paramsCaptor.getAllValues().get(2).get("sourceId"));
  }

  @Test
  void read_replaysDeltaRowsOverCheckpointStateThenFiltersAndSorts() {
    var checkpointA = event("a", "2026-04-06T10:00:00Z", null);
    var checkpointB = event("b", "2026-04-06T10:00:01Z", null);
    var deltaA = event("a", "2026-04-06T10:02:00Z", null);
    var staleBeforeWindow = event("stale", "2026-04-06T10:00:02Z", "2026-04-06T10:01:00Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(checkpointA, checkpointB, staleBeforeWindow), List.of(deltaA));

    var page = reader.read(context(), chunk(), 10);

    assertEquals(List.of(checkpointB, deltaA), page.rows());
    assertFalse(page.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(2))
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertEquals(PostGisHistoricalReadSql.CHECKPOINT_SQL, sqlCaptor.getAllValues().get(0));
    assertEquals(context().singleSourceId(), paramsCaptor.getAllValues().get(0).get("sourceId"));
    assertTrue(sqlCaptor.getAllValues().get(1).contains("WITH delta_rows AS"));
    assertEquals(
        List.of(context().singleSourceId()), paramsCaptor.getAllValues().get(1).get("sourceIds"));
    assertEquals(Timestamp.from(chunk().from()), paramsCaptor.getAllValues().get(1).get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getAllValues().get(1).get("to"));
    assertEquals(10_001, paramsCaptor.getAllValues().get(1).get("limit"));
  }

  private static HistoricalReadContext context() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    return new HistoricalReadContext(
        List.of(sourceId), sourceId, true, HistoricalResolution.MINUTE_1, true);
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        0,
        "chunk-1",
        null,
        null,
        null,
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"));
  }

  private static HistoricalSourceEvent event(String objectId, String playbackAt, String staleAt) {
    return new HistoricalSourceEvent(
        UUID.randomUUID(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
        objectId,
        Instant.parse(playbackAt),
        Instant.parse(playbackAt),
        null,
        staleAt == null ? null : Instant.parse(staleAt),
        "{}");
  }
}
