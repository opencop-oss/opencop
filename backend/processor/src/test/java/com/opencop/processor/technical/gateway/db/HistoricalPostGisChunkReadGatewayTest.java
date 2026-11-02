// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static com.opencop.processor.technical.gateway.db.PostGisHistoricalReadSql.IS_RESIDUAL_COLUMN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class HistoricalPostGisChunkReadGatewayTest {

  private static final String HELPER_TABLE = "geo.feature_latest_active_helper";
  private static final String WINNERS_DISTINCT_ON = "SELECT DISTINCT ON (source, object_id)";
  private static final String BEFORE_CHUNK_TIME = "2026-04-06T10:01:30Z";
  private static final String HISTORICAL_READ_TOTAL = "opencop_history_read_total";
  private static final String HISTORICAL_READ_PAGES_TOTAL = "opencop_history_read_pages_total";
  private static final String HISTORICAL_READ_ROWS_TOTAL = "opencop_history_read_rows_total";
  private static final String ACTIVE_SNAPSHOT_READ_TOTAL =
      "opencop_history_active_snapshot_read_total";
  private static final String ACTIVE_SNAPSHOT_READ_DURATION =
      "opencop_history_active_snapshot_read_duration_seconds";
  private static final String ACTIVE_SNAPSHOT_ROWS_TOTAL =
      "opencop_history_active_snapshot_rows_total";
  private static final String READ_PATH_TAG = "path";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
  private final HistoricalPostGisChunkReadGateway gateway =
      new HistoricalPostGisChunkReadGateway(
          jdbc,
          transactionManager,
          new HistoricalObservability(new SimpleMeterRegistry()),
          new HistoricalExecutionProperties(),
          io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));

  @BeforeEach
  void wireJdbcTemplate() {
    when(jdbc.getJdbcTemplate()).thenReturn(jdbcTemplate);
  }

  @Test
  void readChunk_whenSourceIdsEmpty_returnsEmptyPageWithoutQueryingDatabase() {
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();

    var page =
        gateway.readActiveSnapshot(
            withSourceIds(HistoricalExecutionContext.from(job), List.of()), chunk, null, 2);

    assertTrue(page.rows().isEmpty());
    assertNull(page.nextCursor());
    assertFalse(page.hasMore());
    verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
  }

  @Test
  void readChunk_queriesOneChunkWindowUsingDeterministicOrderingAndPagination() {
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var sourceIds = List.of(UUID.randomUUID(), UUID.randomUUID());

    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:01Z");
    var third = event("obj-3", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(first, second, third));

    var page =
        gateway.readActiveSnapshot(
            withSourceIds(HistoricalExecutionContext.from(job), sourceIds), chunk, null, 2);

    assertEquals(List.of(first, second), page.rows());
    assertTrue(page.hasMore());
    assertNotNull(page.nextCursor());
    assertEquals(second.cursor(), page.nextCursor());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains(WINNERS_DISTINCT_ON));
    assertTrue(sqlCaptor.getValue().contains("COALESCE(start_at, created_at) < :to"));
    // Interval-overlap contract: an entity is included in the chunk if it was alive at
    // chunk.from(), meaning stale_at must be strictly after chunk.from() (not chunk.to()).
    // Entities that go stale mid-chunk remain in the payload; clients filter at render time.
    assertTrue(sqlCaptor.getValue().contains("stale_at IS NULL OR stale_at > :from"));
    assertTrue(
        sqlCaptor.getValue().contains("JOIN geo.features payloads ON payloads.id = winners.id"));
    assertTrue(
        sqlCaptor
            .getValue()
            .contains("ORDER BY winners.playback_at ASC, winners.object_id ASC, winners.id ASC"));
    assertEquals(sourceIds, paramsCaptor.getValue().get("sourceIds"));
    assertEquals(3, paramsCaptor.getValue().get("limit"));
  }

  @Test
  void readChunk_whenCursorProvided_usesWinnersSqlForMultiSource() {
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var sourceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(second));
    var cursor =
        new HistoricalReadCursor(
            Instant.parse("2026-04-06T10:02:01Z"),
            "obj-1",
            UUID.fromString("550e8400-e29b-41d4-a716-446655440999"));

    var page =
        gateway.readActiveSnapshot(
            withSourceIds(HistoricalExecutionContext.from(job), sourceIds), chunk, cursor, 1);

    assertEquals(List.of(second), page.rows());
    assertFalse(page.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains("SELECT DISTINCT ON (source, object_id)"));
    assertTrue(sqlCaptor.getValue().contains("winners.playback_at > :cursorPlaybackAt"));
    assertTrue(sqlCaptor.getValue().contains("object_id > :cursorObjectId"));
    assertTrue(sqlCaptor.getValue().contains("playback_at > :cursorPlaybackAt"));
    assertFalse(sqlCaptor.getValue().contains("geo.feature_latest_active_helper"));
    assertEquals("obj-1", paramsCaptor.getValue().get("cursorObjectId"));
    assertEquals(cursor.eventId(), paramsCaptor.getValue().get("cursorEventId"));
    assertEquals(sourceIds, paramsCaptor.getValue().get("sourceIds"));
  }

  @Test
  void readChunk_whenSingleSourceCursorUsesHelperSqlPagination() {
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(first, second), List.of(second));

    var page1 = gateway.readActiveSnapshot(HistoricalExecutionContext.from(job), chunk, null, 1);
    var page2 =
        gateway.readActiveSnapshot(
            HistoricalExecutionContext.from(job), chunk, page1.nextCursor(), 1);

    assertEquals(List.of(first), page1.rows());
    assertTrue(page1.hasMore());
    assertEquals(List.of(second), page2.rows());
    assertFalse(page2.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(2))
        .execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getAllValues().get(0).contains(HELPER_TABLE));
    assertTrue(sqlCaptor.getAllValues().get(0).contains("LIMIT :limit"));
    assertTrue(sqlCaptor.getAllValues().get(1).contains(HELPER_TABLE));
    assertTrue(
        sqlCaptor.getAllValues().get(1).contains("all_rows.playback_at > :cursorPlaybackAt"));
    assertFalse(
        sqlCaptor.getAllValues().stream()
            .anyMatch(sql -> sql.contains("SELECT DISTINCT ON (source, object_id)")));
  }

  @Test
  void readChunk_qualifiesRowsUsingIntervalOverlapWindow() {
    // A multi-source (LAYER) job bypasses the helper fast path and issues a single
    // WINNERS_SQL query. This lets the test verify with a narrow assertion that the
    // interval-overlap predicate (alive at chunk.from, not chunk.to) appears in
    // exactly the one SQL sent to the database.
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var sourceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-carried", BEFORE_CHUNK_TIME)));

    gateway.readActiveSnapshot(
        withSourceIds(HistoricalExecutionContext.from(job), sourceIds), chunk, null, 10);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    // Interval-overlap contract: an entity is included in the chunk if it was alive at
    // chunk.from(), meaning stale_at must be strictly after chunk.from() (not chunk.to()).
    // Entities that go stale mid-chunk remain in the payload; clients filter at render time.
    assertTrue(
        sqlCaptor.getValue().contains("stale_at IS NULL OR stale_at > :from"),
        "WINNERS_SQL must enforce the interval-overlap alive-at-from predicate");
    assertEquals(Timestamp.from(chunk.from()), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk.to()), paramsCaptor.getValue().get("to"));
  }

  @Test
  void readActiveSnapshot_alwaysUsesWinnersSqlNotHelper() {
    // Multi-source (LAYER) jobs must never touch the helper table. The helper fast path
    // is restricted to single-source jobs because helper rows are keyed per-source and
    // a multi-source query would require a cross-source merge that the helper cannot
    // provide. The standard WINNERS_SQL IN (:sourceIds) shape must be used instead.
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var sourceId1 = UUID.randomUUID();
    var sourceId2 = UUID.randomUUID();
    var context =
        new HistoricalReadContext(List.of(sourceId1, sourceId2), null, false, null, false);
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", "2026-04-06T10:02:01Z")));

    gateway.readActiveSnapshot(context, HistoricalExecutionContext.from(job), chunk, null, 2);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc).execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertFalse(
        sqlCaptor.getValue().contains(HELPER_TABLE),
        "Multi-source jobs must not query the helper table");
    assertTrue(
        sqlCaptor.getValue().contains(WINNERS_DISTINCT_ON),
        "Multi-source jobs must use the standard WINNERS_SQL DISTINCT ON scan");
  }

  @Test
  void readActiveSnapshot_singleSourceNoCursor_queriesHelperTableForFastPath() {
    // Single-source jobs with no active cursor route through the helper-based fast path.
    // The first SQL issued must target geo.feature_latest_active_helper (stable-winner query),
    // not the full DISTINCT ON scan, because the helper table can answer the stable-winner
    // classification cheaply without scanning all of geo.features.
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            true,
            null,
            false);
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", "2026-04-06T10:02:01Z")), List.of(), List.of());

    gateway.readActiveSnapshot(context, HistoricalExecutionContext.from(job), chunk, null, 2);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, atLeastOnce())
        .execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getAllValues().getFirst().contains(HELPER_TABLE));
  }

  @Test
  void readActiveSnapshot_withCheckpointEnabled_queriesCheckpointTableBeforeDeltaReplay() {
    // Checkpoint path is attempted first (before the helper fast path). When the
    // checkpoint table has rows that survive the interval-overlap filter, the result
    // is returned immediately without touching geo.features or the helper table.
    // The event at 10:01:30 has staleAt = 10:02:30, which is strictly after
    // chunk.from() = 10:02:00, so it passes the filter and the checkpoint page is
    // returned without falling through to the fast path.
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var first = event("obj-1", "2026-04-06T10:01:01Z");
    var second = event("obj-2", "2026-04-06T10:01:30Z");
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            HistoricalResolution.MINUTE_1,
            true);
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(first, second), List.of(), List.of(first, second), List.of());

    var page1 =
        gateway.readActiveSnapshot(context, HistoricalExecutionContext.from(job), chunk, null, 1);
    var page2 =
        gateway.readActiveSnapshot(
            context, HistoricalExecutionContext.from(job), chunk, page1.nextCursor(), 1);

    assertEquals(List.of(first), page1.rows());
    assertTrue(page1.hasMore());
    assertEquals(List.of(second), page2.rows());
    assertFalse(page2.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(4))
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getAllValues().get(0).contains("geo.feature_history_checkpoint"));
    assertTrue(sqlCaptor.getAllValues().get(2).contains("geo.feature_history_checkpoint"));
    assertFalse(
        sqlCaptor.getAllValues().stream()
            .anyMatch(sql -> sql.contains("geo.feature_latest_active_helper")));
    assertFalse(
        sqlCaptor.getAllValues().stream()
            .anyMatch(sql -> sql.contains("SELECT DISTINCT ON (source, object_id)")));
    assertEquals(context.singleSourceId(), paramsCaptor.getAllValues().get(0).get("sourceId"));
    assertEquals(context.singleSourceId(), paramsCaptor.getAllValues().get(2).get("sourceId"));
  }

  @Test
  void readActiveSnapshot_withCheckpointEnabled_fallsBackToHelperWhenCheckpointRowsAbsent() {
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var helperRow = event("helper-obj", BEFORE_CHUNK_TIME);
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            HistoricalResolution.MINUTE_1,
            true);
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(), List.of(helperRow));

    var page =
        gateway.readActiveSnapshot(context, HistoricalExecutionContext.from(job), chunk, null, 1);

    assertEquals(List.of(helperRow), page.rows());
    assertFalse(page.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(2))
        .execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getAllValues().get(0).contains("geo.feature_history_checkpoint"));
    assertTrue(sqlCaptor.getAllValues().get(1).contains(HELPER_TABLE));
    assertTrue(sqlCaptor.getAllValues().get(1).contains("LIMIT :limit"));
    assertFalse(
        sqlCaptor.getAllValues().stream()
            .anyMatch(sql -> sql.contains("SELECT DISTINCT ON (source, object_id)")));
  }

  @Test
  void
      readActiveSnapshot_withCheckpointEnabled_fallsBackToHelperForCursorPagesWhenCheckpointRowsAbsent() {
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var helperRowOne = event("helper-obj-one", BEFORE_CHUNK_TIME);
    var helperRowTwo = event("helper-obj-two", "2026-04-06T10:01:31Z");
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            HistoricalResolution.MINUTE_1,
            true);
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(), List.of(helperRowOne, helperRowTwo), List.of(), List.of(helperRowTwo));

    var page1 =
        gateway.readActiveSnapshot(context, HistoricalExecutionContext.from(job), chunk, null, 1);
    var page2 =
        gateway.readActiveSnapshot(
            context, HistoricalExecutionContext.from(job), chunk, page1.nextCursor(), 1);

    assertEquals(List.of(helperRowOne), page1.rows());
    assertTrue(page1.hasMore());
    assertEquals(List.of(helperRowTwo), page2.rows());
    assertFalse(page2.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(4))
        .execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getAllValues().get(0).contains("geo.feature_history_checkpoint"));
    assertTrue(sqlCaptor.getAllValues().get(1).contains(HELPER_TABLE));
    assertTrue(sqlCaptor.getAllValues().get(2).contains("geo.feature_history_checkpoint"));
    assertTrue(sqlCaptor.getAllValues().get(3).contains(HELPER_TABLE));
    assertTrue(
        sqlCaptor.getAllValues().get(3).contains("all_rows.playback_at > :cursorPlaybackAt"));
    assertFalse(
        sqlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains(WINNERS_DISTINCT_ON)));
  }

  @Test
  void helperFastPathDispatchedForSingleSourceJob() {
    // A single-source job with no active cursor must route through the helper fast path.
    // The first SQL issued must query geo.feature_latest_active_helper for stable-winner
    // classification rather than running the full DISTINCT ON scan on geo.features.
    var sourceId = UUID.randomUUID();
    var job = job(SubjectType.SOURCE, sourceId);
    var chunk = chunk();
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", BEFORE_CHUNK_TIME)), List.of(), List.of());

    gateway.readActiveSnapshot(HistoricalExecutionContext.from(job), chunk, null, 10);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc, atLeastOnce())
        .execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertTrue(
        sqlCaptor.getAllValues().getFirst().contains(HELPER_TABLE),
        "First query must target the helper table for the stable-winner fast path");
  }

  @Test
  void helperFastPathSkippedForMultiSourceJob() {
    // Multi-source jobs share helper rows across sources via a different access pattern;
    // the single-source fast path must not engage for them. Multi-source jobs must always
    // use the WINNERS_SQL IN (:sourceIds) shape which covers all sources in one scan.
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var sourceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", "2026-04-06T10:02:01Z")));

    gateway.readActiveSnapshot(
        withSourceIds(HistoricalExecutionContext.from(job), sourceIds), chunk, null, 10);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc).execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    assertFalse(
        sqlCaptor.getValue().contains(HELPER_TABLE),
        "Multi-source jobs must not engage the helper fast path");
    assertTrue(
        sqlCaptor.getValue().contains(WINNERS_DISTINCT_ON),
        "Multi-source jobs must use the standard WINNERS_SQL DISTINCT ON scan");
  }

  @Test
  void readActiveSnapshot_recordsActiveHelperPathForSingleSourcePages() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", "2026-04-06T10:02:01Z")));

    var page =
        metricsGateway.readActiveSnapshot(
            new HistoricalReadContext(
                List.of(job.subjects().getFirst().subjectId()),
                job.subjects().getFirst().subjectId(),
                false,
                null,
                false),
            HistoricalExecutionContext.from(job),
            chunk,
            null,
            2);

    assertFalse(page.rows().isEmpty());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_HELPER")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_HELPER")
            .counter()
            .count());
    int helperRowCount = page.rows().size();
    assertEquals(
        (double) helperRowCount,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_HELPER")
            .counter()
            .count());
    verify(jdbc).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
  }

  @Test
  void readActiveSnapshot_recordsActiveResidualPathWhenResidualWinnersAreQueried() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var stableRow = event("stable-obj", "2026-04-06T10:02:01Z");
    var residualRow = event("residual-obj", "2026-04-06T10:02:02Z");
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(stableRow, NamedParameterJdbcExecuteStub.residualRow(residualRow)));

    var page =
        metricsGateway.readActiveSnapshot(
            new HistoricalReadContext(
                List.of(job.subjects().getFirst().subjectId()),
                job.subjects().getFirst().subjectId(),
                false,
                null,
                false),
            HistoricalExecutionContext.from(job),
            chunk,
            null,
            10);

    assertEquals(List.of(stableRow, residualRow), page.rows());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .counter()
            .count());
    assertEquals(
        2.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .counter()
            .count());
    verify(jdbc).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
  }

  @Test
  void readDelta_recordsEventDeltaPathMetricsWithReturnedRowCount() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", "2026-04-06T10:02:01Z")));

    var page =
        metricsGateway.readDelta(
            new HistoricalReadContext(
                List.of(job.subjects().getFirst().subjectId()),
                job.subjects().getFirst().subjectId(),
                false,
                null,
                false),
            HistoricalExecutionContext.from(job),
            chunk,
            Instant.parse("2026-04-06T10:01:00Z"),
            null,
            2);

    assertEquals(1, page.rows().size());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA_EVENT").counter().count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "DELTA_EVENT")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "DELTA_EVENT")
            .counter()
            .count());
    assertEquals(
        1.0d, registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
  }

  @Test
  void
      streamActiveSnapshot_singleSourceStreamsWithResidualRowsUsingStreamingReaderAndRecordsResidualPath()
          throws SQLException {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            null,
            false);
    var chunk = chunk();
    var residualRow = event("residual-obj", "2026-04-06T10:02:10Z");
    var stableRow = event("stable-obj", "2026-04-06T10:02:30Z");
    reset(jdbc);
    wireJdbcTemplate();
    var statement = mockStreamingStatement(List.of(residualRow, stableRow), List.of(true, false));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              PreparedStatementCallback<Void> callback = invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    var consumed = new ArrayList<HistoricalSourceEvent>();
    metricsGateway.streamActiveSnapshot(
        context, HistoricalExecutionContext.from(job), chunk, null, 2, consumed::add);

    assertEquals(List.of(residualRow, stableRow), consumed);
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .counter()
            .count());
    assertEquals(
        2.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(ACTIVE_SNAPSHOT_READ_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_HELPER", "outcome", "started")
            .counter()
            .count());
    assertNull(
        registry
            .find(ACTIVE_SNAPSHOT_READ_DURATION)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL")
            .timer());
    assertEquals(
        1L,
        registry
            .find(ACTIVE_SNAPSHOT_READ_DURATION)
            .tags(READ_PATH_TAG, "ACTIVE_HELPER", "outcome", "success")
            .timer()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(ACTIVE_SNAPSHOT_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL", "row_kind", "stable")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(ACTIVE_SNAPSHOT_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_RESIDUAL", "row_kind", "residual")
            .counter()
            .count());
    verify(jdbc).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
    verify(jdbc, times(0)).query(anyString(), anyMap(), any(RowMapper.class));
  }

  @Test
  void readActiveSnapshot_whenDatabaseReadFails_recordsStartAndFailureTelemetry() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var context =
        new HistoricalReadContext(List.of(UUID.randomUUID(), UUID.randomUUID()), null, false);
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();
    var failure = new QueryTimeoutException("active snapshot timed out");
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenThrow(failure);

    var thrown =
        assertThrows(
            QueryTimeoutException.class,
            () ->
                metricsGateway.readActiveSnapshot(
                    context, HistoricalExecutionContext.from(job), chunk, null, 2));

    assertEquals(failure, thrown);
    assertEquals(
        1.0d,
        registry
            .find(ACTIVE_SNAPSHOT_READ_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_FULL", "outcome", "started")
            .counter()
            .count());
    assertNull(
        registry
            .find(ACTIVE_SNAPSHOT_READ_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_FULL", "outcome", "error")
            .counter());
    assertEquals(
        1L,
        registry
            .find(ACTIVE_SNAPSHOT_READ_DURATION)
            .tags(READ_PATH_TAG, "ACTIVE_FULL", "outcome", "error")
            .timer()
            .count());
  }

  @Test
  void streamActiveSnapshot_multiSourceFallsBackToPagingAndRecordsFullPath() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var context =
        new HistoricalReadContext(List.of(UUID.randomUUID(), UUID.randomUUID()), null, false);
    var chunk = chunk();
    var row = event("obj-1", "2026-04-06T10:02:01Z");
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(row));

    var consumed = new ArrayList<HistoricalSourceEvent>();
    metricsGateway.streamActiveSnapshot(
        context, HistoricalExecutionContext.from(job), chunk, null, 2, consumed::add);

    assertEquals(List.of(row), consumed);
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "ACTIVE_FULL").counter().count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_FULL")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_FULL")
            .counter()
            .count());
  }

  @Test
  void streamActiveSnapshot_checkpointContextStreamsCheckpointPathAndRecordsCheckpointPathMetrics()
      throws SQLException {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            HistoricalResolution.MINUTE_1,
            true);
    var chunk = chunk();
    var checkpointRow = event("checkpoint-obj", BEFORE_CHUNK_TIME);
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(checkpointRow), List.of());

    var consumed = new ArrayList<HistoricalSourceEvent>();
    metricsGateway.streamActiveSnapshot(
        context, HistoricalExecutionContext.from(job), chunk, null, 2, consumed::add);

    assertEquals(List.of(checkpointRow), consumed);
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "CHECKPOINT").counter().count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "CHECKPOINT")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "CHECKPOINT")
            .counter()
            .count());
    verify(jdbc, times(2)).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
    verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
  }

  @Test
  void streamActiveSnapshot_executesInsideRepeatableReadTransaction() throws SQLException {
    var job = job(SubjectType.SOURCE, UUID.fromString("550e8400-e29b-41d4-a716-446655440310"));
    var chunk = chunk();
    var context =
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            true,
            null,
            false);
    reset(jdbc);
    wireJdbcTemplate();
    transactionManager.reset();
    var statement = mockStreamingStatement(List.of(event("obj-1", "2026-04-06T10:02:01Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              PreparedStatementCallback<Void> callback = invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    gateway.streamActiveSnapshot(
        context, HistoricalExecutionContext.from(job), chunk, null, 2, row -> {});
    assertEquals(1, transactionManager.getTransactionCalls());
  }

  @Test
  void streamActiveSnapshot_withEmptyContextSkipsQueryAndDoesNotNotifyConsumer() {
    var emptyContext = new HistoricalReadContext(List.of(), null, false);
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var consumed = new ArrayList<HistoricalSourceEvent>();

    gateway.streamActiveSnapshot(
        emptyContext, HistoricalExecutionContext.from(job), chunk, null, 2, consumed::add);

    assertEquals(0, consumed.size());
    verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
  }

  @Test
  void streamDelta_withEmptyContextSkipsQueryAndDoesNotNotifyConsumer() {
    var emptyContext = new HistoricalReadContext(List.of(), null, false);
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var consumed = new ArrayList<HistoricalSourceEvent>();

    gateway.streamDelta(
        emptyContext,
        HistoricalExecutionContext.from(job),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        consumed::add);

    assertEquals(0, consumed.size());
    verify(jdbc, never()).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
    verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
  }

  @Test
  void streamDelta_executesInsideRepeatableReadTransaction() throws SQLException {
    var job = job(SubjectType.SOURCE, UUID.fromString("550e8400-e29b-41d4-a716-446655440303"));
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();
    transactionManager.reset();

    var statement = mockStreamingStatement(List.of(event("obj-1", "2026-04-06T10:02:01Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    gateway.streamDelta(
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            null,
            false),
        HistoricalExecutionContext.from(job),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        row -> {});
    assertEquals(1, transactionManager.getTransactionCalls());
  }

  @Test
  void streamDelta_recordsDeltaPathMetricsWithStreamedRowCount() throws SQLException {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.fromString("550e8400-e29b-41d4-a716-446655440304"));
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();

    var statement =
        mockStreamingStatement(
            List.of(
                event("obj-1", "2026-04-06T10:02:01Z"), event("obj-2", "2026-04-06T10:02:02Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    var consumed = new ArrayList<HistoricalSourceEvent>();
    metricsGateway.streamDelta(
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            null,
            false),
        HistoricalExecutionContext.from(job),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        consumed::add);

    assertEquals(2, consumed.size());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA_EVENT").counter().count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "DELTA_EVENT")
            .counter()
            .count());
    assertEquals(
        2.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "DELTA_EVENT")
            .counter()
            .count());
    assertEquals(
        1.0d, registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
    assertEquals(
        2.0d,
        registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
  }

  @Test
  void readStateDelta_usesLatestStateDeltaSqlAndRecordsStateDeltaMetrics() throws SQLException {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.fromString("550e8400-e29b-41d4-a716-446655440305"));
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();

    var statement =
        mockStreamingStatement(
            List.of(
                event("obj-1", "2026-04-06T10:02:01Z"), event("obj-2", "2026-04-06T10:02:02Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    var consumed = new ArrayList<HistoricalSourceEvent>();
    metricsGateway.readStateDelta(
        HistoricalExecutionContext.from(job),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        consumed::add);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains("WITH changed_objects AS MATERIALIZED"));
    assertTrue(sql.contains("SELECT DISTINCT"));
    assertTrue(sql.contains("FROM geo.features features"));
    assertTrue(sql.contains("JOIN LATERAL"));
    assertTrue(sql.contains("features.source = changed_objects.source"));
    assertTrue(sql.contains("features.object_id = changed_objects.object_id"));
    assertTrue(sql.contains("COALESCE(features.start_at, features.created_at) >= :from"));
    assertTrue(sql.contains("COALESCE(features.start_at, features.created_at) < :to"));
    assertTrue(sql.contains("ORDER BY COALESCE(features.start_at, features.created_at) DESC"));
    assertTrue(sql.contains("JOIN geo.features payloads ON payloads.id = winners.id"));
    assertTrue(sql.contains("payloads.payload::text AS payload_json"));
    assertFalse(sql.contains("ROW_NUMBER() OVER"));
    assertFalse(sql.contains("WHERE winners.row_rank = 1"));
    assertFalse(sql.contains("winners.payload"));
    assertFalse(sql.contains("geo.feature_latest_active_helper"));
    assertFalse(sql.contains("FROM delta_rows"));
    assertEquals(2, consumed.size());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA_STATE").counter().count());
    assertEquals(
        2.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "DELTA_STATE")
            .counter()
            .count());
    assertEquals(
        1.0d, registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
    assertEquals(
        2.0d,
        registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
    assertEquals(
        List.of(job.subjects().getFirst().subjectId()), paramsCaptor.getValue().get("sourceIds"));
    assertEquals(
        Timestamp.from(Instant.parse("2026-04-06T10:00:00Z")), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk.to()), paramsCaptor.getValue().get("to"));
  }

  @Test
  void stateDeltaReadPath_selectsHelperDomainOnlyForSingleSourceDayOne() {
    var sourceId = UUID.randomUUID();
    var singleSourceContext = new HistoricalReadContext(List.of(sourceId), sourceId, false);
    var multiSourceContext =
        new HistoricalReadContext(List.of(UUID.randomUUID(), UUID.randomUUID()), null, false);
    var dayOneContext =
        withResolution(
            HistoricalExecutionContext.from(job(SubjectType.SOURCE, sourceId)),
            HistoricalResolution.DAY_1);
    var hourOneContext = withResolution(dayOneContext, HistoricalResolution.HOUR_1);

    assertEquals(
        PostGisHistoricalDeltaReader.StateDeltaReadPath.HELPER_DOMAIN,
        HistoricalPostGisChunkReadGateway.stateDeltaReadPath(singleSourceContext, dayOneContext));
    assertEquals(
        PostGisHistoricalDeltaReader.StateDeltaReadPath.CHANGED_OBJECT,
        HistoricalPostGisChunkReadGateway.stateDeltaReadPath(singleSourceContext, hourOneContext));
    assertEquals(
        PostGisHistoricalDeltaReader.StateDeltaReadPath.CHANGED_OBJECT,
        HistoricalPostGisChunkReadGateway.stateDeltaReadPath(multiSourceContext, dayOneContext));
  }

  @Test
  void streamLatestStateDelta_dayOneSingleSourceUsesHelperDomainSql() throws SQLException {
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440307");
    var job = job(SubjectType.SOURCE, sourceId);
    var context = new HistoricalReadContext(List.of(sourceId), sourceId, false);
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();

    var statement = mockStreamingStatement(List.of(event("obj-1", "2026-04-06T10:02:01Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    gateway.streamLatestStateDelta(
        context,
        withResolution(HistoricalExecutionContext.from(job), HistoricalResolution.DAY_1),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        row -> {});

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains(HELPER_TABLE));
    assertTrue(sql.contains("WHERE helper.source = :sourceId"));
    assertFalse(sql.contains("WITH changed_objects AS MATERIALIZED"));
    assertEquals(sourceId, paramsCaptor.getValue().get("sourceId"));
    assertFalse(paramsCaptor.getValue().containsKey("sourceIds"));
  }

  @Test
  void streamLatestStateDelta_hourOneSingleSourceStillUsesChangedObjectSql() throws SQLException {
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440308");
    var job = job(SubjectType.SOURCE, sourceId);
    var context = new HistoricalReadContext(List.of(sourceId), sourceId, false);
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();

    var statement = mockStreamingStatement(List.of(event("obj-1", "2026-04-06T10:02:01Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    gateway.streamLatestStateDelta(
        context,
        withResolution(HistoricalExecutionContext.from(job), HistoricalResolution.HOUR_1),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        row -> {});

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc).execute(sqlCaptor.capture(), anyMap(), any(PreparedStatementCallback.class));
    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains("WITH changed_objects AS MATERIALIZED"));
    assertFalse(sql.contains(HELPER_TABLE));
  }

  @Test
  void streamLatestStateDelta_directMultiSourceStillUsesChangedObjectSql() throws SQLException {
    var sourceIds =
        List.of(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440309"),
            UUID.fromString("550e8400-e29b-41d4-a716-44665544030a"));
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var context = new HistoricalReadContext(sourceIds, null, false);
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();

    var statement = mockStreamingStatement(List.of(event("obj-1", "2026-04-06T10:02:01Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    gateway.streamLatestStateDelta(
        context,
        withResolution(
            withSourceIds(HistoricalExecutionContext.from(job), sourceIds),
            HistoricalResolution.DAY_1),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        row -> {});

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains("WITH changed_objects AS MATERIALIZED"));
    assertFalse(sql.contains(HELPER_TABLE));
    assertEquals(sourceIds, paramsCaptor.getValue().get("sourceIds"));
  }

  @Test
  void streamLatestStateDelta_recordsStateDeltaMetrics() throws SQLException {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.fromString("550e8400-e29b-41d4-a716-446655440306"));
    var chunk = chunk();
    reset(jdbc);
    wireJdbcTemplate();

    var statement =
        mockStreamingStatement(
            List.of(
                event("winner-1", "2026-04-06T10:02:01Z"),
                event("winner-2", "2026-04-06T10:02:02Z"),
                event("winner-3", "2026-04-06T10:02:03Z")));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var callback = (PreparedStatementCallback<Void>) invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    var consumed = new ArrayList<HistoricalSourceEvent>();
    metricsGateway.streamLatestStateDelta(
        new HistoricalReadContext(
            List.of(job.subjects().getFirst().subjectId()),
            job.subjects().getFirst().subjectId(),
            false,
            null,
            false),
        HistoricalExecutionContext.from(job),
        chunk,
        Instant.parse("2026-04-06T10:00:00Z"),
        2,
        consumed::add);

    assertEquals(3, consumed.size());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA_STATE").counter().count());
    assertEquals(
        3.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "DELTA_STATE")
            .counter()
            .count());
    assertEquals(
        1.0d, registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
    assertEquals(
        3.0d,
        registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(READ_PATH_TAG, "DELTA").counter().count());
  }

  @Test
  void readActiveSnapshot_recordsActiveFullPathForMultiSourcePages() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.LAYER, UUID.randomUUID());
    var chunk = chunk();
    var sourceIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(
        jdbc, List.of(event("obj-1", "2026-04-06T10:02:01Z")));

    var page =
        metricsGateway.readActiveSnapshot(
            new HistoricalReadContext(sourceIds, null, false, null, false),
            HistoricalExecutionContext.from(job),
            chunk,
            null,
            2);

    assertEquals(1, page.rows().size());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "ACTIVE_FULL").counter().count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_FULL")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "ACTIVE_FULL")
            .counter()
            .count());
  }

  @Test
  void readActiveSnapshot_recordsCheckpointPathWhenCheckpointRowsAreReturned() {
    var registry = new SimpleMeterRegistry();
    var metricsGateway = gatewayWithMetrics(registry);
    var job = job(SubjectType.SOURCE, UUID.randomUUID());
    var chunk = chunk();
    var checkpointRow = event("checkpoint-obj", BEFORE_CHUNK_TIME);
    reset(jdbc);
    wireJdbcTemplate();
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(checkpointRow), List.of());

    var page =
        metricsGateway.readActiveSnapshot(
            new HistoricalReadContext(
                List.of(job.subjects().getFirst().subjectId()),
                job.subjects().getFirst().subjectId(),
                false,
                HistoricalResolution.MINUTE_1,
                true),
            HistoricalExecutionContext.from(job),
            chunk,
            null,
            2);

    assertEquals(List.of(checkpointRow), page.rows());
    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(READ_PATH_TAG, "CHECKPOINT").counter().count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(READ_PATH_TAG, "CHECKPOINT")
            .counter()
            .count());
    assertEquals(
        1.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(READ_PATH_TAG, "CHECKPOINT")
            .counter()
            .count());
  }

  private static HistoricalTracksJobSubmission job(SubjectType subjectType, UUID subjectId) {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        subjectType,
        subjectId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of());
  }

  private static HistoricalExecutionContext withSourceIds(
      HistoricalExecutionContext context, List<UUID> sourceIds) {
    return new HistoricalExecutionContext(
        context.jobId(),
        context.parentJobId(),
        context.requestId(),
        context.issuer(),
        context.requestedBy(),
        context.requestedAt(),
        context.requestedFrom(),
        context.requestedTo(),
        context.effectiveFrom(),
        context.effectiveTo(),
        context.resolution(),
        context.plannedChunks(),
        context.filters(),
        context.subjectType(),
        context.subjectId(),
        sourceIds,
        context.windowPlan());
  }

  private static HistoricalExecutionContext withResolution(
      HistoricalExecutionContext context, HistoricalResolution resolution) {
    return new HistoricalExecutionContext(
        context.jobId(),
        context.parentJobId(),
        context.requestId(),
        context.issuer(),
        context.requestedBy(),
        context.requestedAt(),
        context.requestedFrom(),
        context.requestedTo(),
        context.effectiveFrom(),
        context.effectiveTo(),
        resolution,
        context.plannedChunks(),
        context.filters(),
        context.subjectType(),
        context.subjectId(),
        context.sourceIds(),
        context.windowPlan());
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        0,
        "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:02:00Z",
        null,
        null,
        null,
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"));
  }

  private static HistoricalSourceEvent event(String objectId, String playbackAt) {
    Instant playback = Instant.parse(playbackAt);
    return new HistoricalSourceEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        objectId,
        playback,
        playback,
        playback,
        playback.plusSeconds(60),
        "{\"objectId\":\"%s\"}".formatted(objectId));
  }

  private PreparedStatement mockStreamingStatement(List<HistoricalSourceEvent> rows)
      throws SQLException {
    return mockStreamingStatement(rows, List.of(false));
  }

  private PreparedStatement mockStreamingStatement(
      List<HistoricalSourceEvent> rows, List<Boolean> residualFlags) throws SQLException {
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    AtomicInteger rowIndex = new AtomicInteger(-1);

    when(resultSet.next()).thenAnswer(_ -> rowIndex.incrementAndGet() < rows.size());
    when(resultSet.getObject("id", UUID.class)).thenAnswer(_ -> rows.get(rowIndex.get()).eventId());
    when(resultSet.getObject("source", UUID.class))
        .thenAnswer(_ -> rows.get(rowIndex.get()).sourceId());
    when(resultSet.getString("object_id")).thenAnswer(_ -> rows.get(rowIndex.get()).objectId());
    when(resultSet.getTimestamp("playback_at"))
        .thenAnswer(_ -> Timestamp.from(rows.get(rowIndex.get()).playbackAt()));
    when(resultSet.getTimestamp("created_at"))
        .thenAnswer(_ -> Timestamp.from(rows.get(rowIndex.get()).createdAt()));
    when(resultSet.getTimestamp("start_at"))
        .thenAnswer(
            _ ->
                rows.get(rowIndex.get()).startAt() == null
                    ? null
                    : Timestamp.from(rows.get(rowIndex.get()).startAt()));
    when(resultSet.getTimestamp("stale_at"))
        .thenAnswer(
            _ ->
                rows.get(rowIndex.get()).staleAt() == null
                    ? null
                    : Timestamp.from(rows.get(rowIndex.get()).staleAt()));
    when(resultSet.getString("payload_json"))
        .thenAnswer(_ -> rows.get(rowIndex.get()).payloadJson());
    when(resultSet.getBoolean(IS_RESIDUAL_COLUMN))
        .thenAnswer(_ -> residualFlags.get(rowIndex.get()));

    when(statement.executeQuery()).thenReturn(resultSet);

    return statement;
  }

  private HistoricalPostGisChunkReadGateway gatewayWithMetrics(SimpleMeterRegistry meterRegistry) {
    return new HistoricalPostGisChunkReadGateway(
        jdbc,
        transactionManager,
        new HistoricalObservability(meterRegistry),
        new HistoricalExecutionProperties(),
        io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
  }

  private static class RecordingTransactionManager implements PlatformTransactionManager {
    private int getTransactionCalls = 0;

    int getTransactionCalls() {
      return getTransactionCalls;
    }

    void reset() {
      getTransactionCalls = 0;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      getTransactionCalls++;
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      // no-op for unit validation
    }

    @Override
    public void rollback(TransactionStatus status) {
      // no-op for unit validation
    }
  }
}
