// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static com.opencop.processor.technical.gateway.db.PostGisHistoricalReadSql.IS_RESIDUAL_COLUMN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostGisHistoricalActiveSnapshotReaderTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final PostGisHistoricalActiveSnapshotReader reader =
      new PostGisHistoricalActiveSnapshotReader(jdbc, Duration.ofSeconds(7));

  @Test
  void read_whenMultipleSources_queriesWinnersAndPagesRows() {
    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(first, second));

    var page = reader.read(multiSourceContext(), chunk(), null, 1);

    assertEquals(List.of(first), page.rows());
    assertTrue(page.hasMore());
    assertEquals(first.cursor(), page.nextCursor());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains("SELECT DISTINCT ON (source, object_id)"));
    assertFalse(sqlCaptor.getValue().contains("geo.feature_latest_active_helper"));
    assertEquals(multiSourceContext().sourceIds(), paramsCaptor.getValue().get("sourceIds"));
    assertEquals(Timestamp.from(chunk().from()), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getValue().get("to"));
    assertEquals(2, paramsCaptor.getValue().get("limit"));
  }

  @Test
  void read_whenMultipleSources_appliesQueryTimeoutToPagedWinnerStatement() throws SQLException {
    var statements = NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of());

    reader.read(multiSourceContext(), chunk(), null, 1);

    verify(statements.getFirst()).setQueryTimeout(7);
    verify(jdbc, never()).getJdbcTemplate();
  }

  @Test
  void read_whenMultipleSources_queryWithCursorForPageTwo() {
    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(first, second), List.of(second));

    var page1 = reader.read(multiSourceContext(), chunk(), null, 1);
    var page2 = reader.read(multiSourceContext(), chunk(), page1.nextCursor(), 1);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(2))
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertEquals(List.of(first), page1.rows());
    assertEquals(first.cursor(), page1.nextCursor());
    assertEquals(List.of(second), page2.rows());
    assertFalse(page2.hasMore());
    var pageTwoSql = sqlCaptor.getAllValues().get(1);
    var pageTwoParams = paramsCaptor.getAllValues().get(1);
    assertTrue(pageTwoSql.contains("winners.playback_at > :cursorPlaybackAt"));
    assertTrue(pageTwoSql.contains("SELECT DISTINCT ON (source, object_id)"));
    assertFalse(pageTwoSql.contains("geo.feature_latest_active_helper"));
    assertEquals(
        Timestamp.from(page1.nextCursor().playbackAt()), pageTwoParams.get("cursorPlaybackAt"));
    assertEquals(page1.nextCursor().objectId(), pageTwoParams.get("cursorObjectId"));
    assertEquals(page1.nextCursor().eventId(), pageTwoParams.get("cursorEventId"));
  }

  @Test
  void read_whenSingleSourceCursorPages_queryHelperSqlWithCursorAndLimit() {
    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(first, second), List.of(second));

    var page1 = reader.read(singleSourceContext(), chunk(), null, 1);
    var page2 = reader.read(singleSourceContext(), chunk(), page1.nextCursor(), 1);

    assertEquals(List.of(first), page1.rows());
    assertTrue(page1.hasMore());
    assertEquals(List.of(second), page2.rows());
    assertFalse(page2.hasMore());
    assertNull(page2.nextCursor());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(2))
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(
        sqlCaptor
            .getAllValues()
            .get(0)
            .contains(PostGisHistoricalReadSql.HELPER_WINNERS_SQL.trim()),
        "Paged helper SQL should use the shared helper winner base");
    assertTrue(sqlCaptor.getAllValues().get(0).contains("geo.feature_latest_active_helper"));
    assertTrue(sqlCaptor.getAllValues().get(0).contains("ORDER BY all_rows.playback_at ASC"));
    assertTrue(sqlCaptor.getAllValues().get(0).contains("LIMIT :limit"));
    assertTrue(
        sqlCaptor.getAllValues().get(1).contains("all_rows.playback_at > :cursorPlaybackAt"));
    assertTrue(sqlCaptor.getAllValues().get(1).contains("LIMIT :limit"));
    assertFalse(
        sqlCaptor.getAllValues().stream()
            .anyMatch(sql -> sql.contains("SELECT DISTINCT ON (source, object_id)")));
    assertEquals(2, paramsCaptor.getAllValues().get(0).get("limit"));
    assertEquals(2, paramsCaptor.getAllValues().get(1).get("limit"));
    assertEquals(
        Timestamp.from(page1.nextCursor().playbackAt()),
        paramsCaptor.getAllValues().get(1).get("cursorPlaybackAt"));
  }

  @Test
  void read_whenHelperHasOnlyStableRows_queriesHelperOnce() {
    var stable = event("stable", "2026-04-06T10:01:30Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(stable));

    var page = reader.read(singleSourceContext(), chunk(), null, 10);

    assertEquals(List.of(stable), page.rows());
    assertFalse(page.hasMore());
    assertNull(page.nextCursor());
    verify(jdbc).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
  }

  @Test
  void read_whenHelperFindsResiduals_returnsDatabaseOrderedStableAndResidualWinners() {
    var stableLater = event("stable-later", "2026-04-06T10:01:45Z");
    var residualEarlier = event("residual-earlier", "2026-04-06T10:01:30Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(residualEarlier, stableLater));

    var page = reader.read(singleSourceContext(), chunk(), null, 10);

    assertEquals(List.of(residualEarlier, stableLater), page.rows());
    assertFalse(page.hasMore());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains("geo.feature_latest_active_helper"));
    assertTrue(sqlCaptor.getValue().contains("UNION ALL"));
    assertTrue(sqlCaptor.getValue().contains("ORDER BY all_rows.playback_at ASC"));
    assertTrue(sqlCaptor.getValue().contains("LIMIT :limit"));
    assertEquals(11, paramsCaptor.getValue().get("limit"));
  }

  @Test
  void helperWinnerSql_filtersResidualsWithLatestBeforeToMetadataGate() {
    assertEquals(
        PostGisHistoricalReadSql.HELPER_WINNERS_SQL,
        PostGisHistoricalReadSql.STREAM_HELPER_WINNERS_SQL);
    assertHelperResidualMetadataGate(PostGisHistoricalReadSql.HELPER_WINNERS_SQL);
    assertHelperResidualMetadataGate(PostGisHistoricalReadSql.STREAM_HELPER_WINNERS_SQL);
  }

  @Test
  void helperWinnerSql_assemblesPagedAndStreamingTailsFromSharedBase() {
    var pagedSql = PostGisHistoricalReadSql.helperWinnersSql(null);
    var streamingSql = PostGisHistoricalReadSql.streamHelperWinnersSql(null);

    assertTrue(pagedSql.contains(PostGisHistoricalReadSql.HELPER_WINNERS_SQL.trim()));
    assertTrue(streamingSql.contains(PostGisHistoricalReadSql.HELPER_WINNERS_SQL.trim()));
    assertStreamTopLevelOrder(pagedSql);
    assertStreamTopLevelOrder(streamingSql);
    assertTrue(pagedSql.contains("LIMIT :limit"));
    assertFalse(streamingSql.contains("LIMIT :limit"));
  }

  @Test
  void stream_queriesHelperAndResidualCteWithoutCombinedMaterialization() throws SQLException {
    var stableRow = event("stable", "2026-04-06T10:02:30Z");
    var residualRow = event("residual", "2026-04-06T10:02:10Z");
    PreparedStatement statement =
        mockStreamingStatement(List.of(residualRow, stableRow), List.of(true, false));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              PreparedStatementCallback<Void> callback = invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });

    var consumed = new ArrayList<HistoricalSourceEvent>();
    Consumer<HistoricalSourceEvent> consumer = consumed::add;
    var path =
        reader.streamSingleSourceViaHelperFastPath(
            singleSourceContext(), chunk(), null, 5000, consumer);

    assertEquals(PostGisHistoricalActiveSnapshotReader.ActiveSnapshotReadPath.RESIDUAL, path);
    assertEquals(List.of(residualRow, stableRow), consumed);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    verify(statement).setFetchSize(5000);
    verify(statement).setQueryTimeout(7);
    verify(jdbc, times(0)).query(anyString(), anyMap(), any(RowMapper.class));
    var sql = sqlCaptor.getValue();
    String normalizedSql = sql.toLowerCase(Locale.ROOT);
    assertTrue(normalizedSql.contains("residual_metadata as"), () -> "sql was: " + sql);
    assertTrue(normalizedSql.contains("from residual_metadata"), () -> "sql was: " + sql);
    assertTrue(
        normalizedSql.contains("join geo.features payloads on payloads.id = residual_metadata.id"),
        () -> "sql was: " + sql);
    assertFalse(sql.contains(":residualObjectIds"));
    assertFalse(sql.contains("SELECT DISTINCT ON (source, object_id)"));
    assertStreamTopLevelOrder(sql);
    assertEquals(singleSourceContext().singleSourceId(), paramsCaptor.getValue().get("sourceId"));
    assertEquals(Timestamp.from(chunk().from()), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getValue().get("to"));
  }

  @Test
  void stream_withCursorUsesKeysetPredicateAndMapsRows() throws SQLException {
    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    PreparedStatement statement =
        mockStreamingStatement(List.of(first, second), List.of(true, false));
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              PreparedStatementCallback<Void> callback = invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });
    var cursor = first.cursor();

    var consumed = new ArrayList<HistoricalSourceEvent>();
    Consumer<HistoricalSourceEvent> consumer = consumed::add;
    reader.streamSingleSourceViaHelperFastPath(
        singleSourceContext(), chunk(), cursor, 5000, consumer);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains("all_rows.playback_at > :cursorPlaybackAt"));
    assertEquals(
        Timestamp.from(cursor.playbackAt()), paramsCaptor.getValue().get("cursorPlaybackAt"));
    assertEquals(cursor.objectId(), paramsCaptor.getValue().get("cursorObjectId"));
    assertEquals(cursor.eventId(), paramsCaptor.getValue().get("cursorEventId"));
    assertEquals(List.of(first, second), consumed);
    assertTrue(sqlCaptor.getValue().contains("all_rows.playback_at > :cursorPlaybackAt"));
    assertStreamTopLevelOrder(sqlCaptor.getValue(), true);
    assertTrue(
        sqlCaptor.getValue().contains(PostGisHistoricalReadSql.STREAM_HELPER_WINNERS_SQL.trim()),
        "Streaming SQL should use the shared helper-residual stream query template");
    assertFalse(sqlCaptor.getValue().contains("LIMIT :limit"));
  }

  private static HistoricalReadContext multiSourceContext() {
    return new HistoricalReadContext(
        List.of(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440011")),
        null,
        false);
  }

  private static HistoricalReadContext singleSourceContext() {
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    return new HistoricalReadContext(List.of(sourceId), sourceId, true);
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

  private static HistoricalSourceEvent event(String objectId, String playbackAt) {
    Instant playback = Instant.parse(playbackAt);
    return new HistoricalSourceEvent(
        UUID.randomUUID(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
        objectId,
        playback,
        playback,
        playback,
        playback.plusSeconds(60),
        "{}");
  }

  private static void assertStreamTopLevelOrder(String sql) {
    assertStreamTopLevelOrder(sql, false);
  }

  private static void assertStreamTopLevelOrder(String sql, boolean expectedCursorPredicate) {
    var normalizedSql = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    var expectedOrderClause =
        "order by all_rows.playback_at asc, all_rows.object_id asc, all_rows.id asc";
    var allRowsSubqueryClause = ") all_rows";
    var cursorClause = "all_rows.playback_at > :cursorplaybackat";
    var cursorIndex = normalizedSql.indexOf(cursorClause);
    var subqueryIndex = normalizedSql.lastIndexOf(allRowsSubqueryClause);
    var orderIndex = normalizedSql.indexOf(expectedOrderClause);

    assertTrue(subqueryIndex >= 0, () -> "sql was: " + sql);
    assertTrue(orderIndex > subqueryIndex, () -> "sql was: " + sql);
    if (expectedCursorPredicate) {
      assertTrue(
          cursorIndex > subqueryIndex, () -> "cursor predicate should be in subquery tail: " + sql);
      assertTrue(orderIndex > cursorIndex, () -> "sql was: " + sql);
    }
  }

  private static void assertHelperResidualMetadataGate(String sql) {
    var normalizedSql = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    assertFalse(normalizedSql.contains("exists ( select 1 from geo.features features"));
    assertFalse(sql.contains(":residualObjectIds"));
    assertTrue(normalizedSql.contains("with stable_winners as"));
    assertTrue(normalizedSql.contains("residual_metadata as"));
    assertTrue(normalizedSql.contains("false as " + IS_RESIDUAL_COLUMN));
    assertTrue(normalizedSql.contains("true as " + IS_RESIDUAL_COLUMN));
    assertTrue(normalizedSql.contains("join lateral ( select features.id"));
    assertTrue(normalizedSql.contains("features.object_id = helper.object_id"));
    assertTrue(normalizedSql.contains("coalesce(features.start_at, features.created_at) < :to"));
    assertTrue(
        normalizedSql.contains(
            "order by coalesce(features.start_at, features.created_at) desc, features.created_at desc, features.id desc limit 1"));
    assertTrue(normalizedSql.contains("(latest.stale_at is null or latest.stale_at > :from)"));
    assertFalse(normalizedSql.contains("features.payload,"));
    assertTrue(
        normalizedSql.contains("join geo.features payloads on payloads.id = residual_metadata.id"));
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
}
