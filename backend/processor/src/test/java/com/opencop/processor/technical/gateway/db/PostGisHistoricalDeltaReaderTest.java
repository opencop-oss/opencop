// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostGisHistoricalDeltaReaderTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final PostGisHistoricalDeltaReader reader =
      new PostGisHistoricalDeltaReader(jdbc, Duration.ofSeconds(11));

  @Test
  void read_queriesDeltaWindowAndPagesResults() {
    var first = event("obj-1", "2026-04-06T10:02:01Z");
    var second = event("obj-2", "2026-04-06T10:02:02Z");
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of(first, second));

    var page = reader.read(context(), chunk(), Instant.parse("2026-04-06T10:00:00Z"), null, 1);

    assertEquals(List.of(first), page.rows());
    assertTrue(page.hasMore());
    assertEquals(first.cursor(), page.nextCursor());

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("WITH delta_rows AS"));
    assertTrue(sqlCaptor.getValue().contains("COALESCE(start_at, created_at) >= :from"));
    assertFalse(sqlCaptor.getValue().contains(":cursorPlaybackAt"));
    assertEquals(context().sourceIds(), paramsCaptor.getValue().get("sourceIds"));
    assertEquals(
        Timestamp.from(Instant.parse("2026-04-06T10:00:00Z")), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getValue().get("to"));
    assertEquals(2, paramsCaptor.getValue().get("limit"));
  }

  @Test
  void read_whenCursorProvided_addsKeysetParameters() {
    var cursor =
        new HistoricalReadCursor(
            Instant.parse("2026-04-06T10:01:00Z"),
            "obj-1",
            UUID.fromString("550e8400-e29b-41d4-a716-446655440099"));
    NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of());

    reader.read(context(), chunk(), Instant.parse("2026-04-06T10:00:00Z"), cursor, 10);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains("delta_rows.playback_at > :cursorPlaybackAt"));
    assertEquals(
        Timestamp.from(Instant.parse("2026-04-06T10:01:00Z")),
        paramsCaptor.getValue().get("cursorPlaybackAt"));
    assertEquals("obj-1", paramsCaptor.getValue().get("cursorObjectId"));
    assertEquals(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440099"),
        paramsCaptor.getValue().get("cursorEventId"));
  }

  @Test
  void read_appliesConfiguredQueryTimeout() throws SQLException {
    var statements = NamedParameterJdbcExecuteStub.stubExecuteRows(jdbc, List.of());

    reader.read(context(), chunk(), Instant.parse("2026-04-06T10:00:00Z"), null, 10);

    verify(statements.getFirst()).setQueryTimeout(11);
    verify(jdbc, never()).query(anyString(), anyMap(), any(RowMapper.class));
  }

  @Test
  void stream_queriesDeltaWindowWithoutCursorOrLimit() {
    mockStreamingQueryExecution(List.of(event("obj-1", "2026-04-06T10:02:01Z")));
    var received = new ArrayList<HistoricalSourceEvent>();

    reader.stream(context(), chunk(), Instant.parse("2026-04-06T10:00:00Z"), received::add, 10);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    assertTrue(sqlCaptor.getValue().contains("FROM delta_rows"));
    assertTrue(sqlCaptor.getValue().contains("ORDER BY delta_rows.playback_at ASC"));
    assertFalse(sqlCaptor.getValue().contains(":cursorPlaybackAt"));
    assertFalse(sqlCaptor.getValue().contains("LIMIT"));
    assertEquals(context().sourceIds(), paramsCaptor.getValue().get("sourceIds"));
    assertEquals(
        Timestamp.from(Instant.parse("2026-04-06T10:00:00Z")), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getValue().get("to"));
    assertFalse(paramsCaptor.getValue().containsKey("cursorPlaybackAt"));
    assertFalse(paramsCaptor.getValue().containsKey("cursorObjectId"));
    assertFalse(paramsCaptor.getValue().containsKey("cursorEventId"));
    assertFalse(paramsCaptor.getValue().containsKey("limit"));
  }

  @Test
  void stream_withFetchSizeAndCallbackMapping() throws SQLException {
    var rows =
        List.of(event("obj-one", "2026-04-06T10:02:01Z"), event("obj-two", "2026-04-06T10:02:02Z"));
    PreparedStatement statement = mockStreamingQueryExecution(rows);
    var received = new ArrayList<HistoricalSourceEvent>();

    reader.stream(context(), chunk(), Instant.parse("2026-04-06T10:00:00Z"), received::add, 5000);

    verify(statement).setFetchSize(5000);
    verify(statement).setQueryTimeout(11);
    assertEquals(rows, received);
  }

  @Test
  void streamLatestState_queriesLatestPerObjectDeltaWindow() throws SQLException {
    var rows =
        List.of(event("obj-one", "2026-04-06T10:02:01Z"), event("obj-two", "2026-04-06T10:02:02Z"));
    PreparedStatement statement = mockStreamingQueryExecution(rows);
    var received = new ArrayList<HistoricalSourceEvent>();

    reader.streamLatestState(
        context(), chunk(), Instant.parse("2026-04-06T10:00:00Z"), received::add, 5000);

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
    assertTrue(sql.contains("ORDER BY winners.playback_at ASC"));
    verify(statement).setFetchSize(5000);
    verify(statement).setQueryTimeout(11);
    assertEquals(context().sourceIds(), paramsCaptor.getValue().get("sourceIds"));
    assertEquals(
        Timestamp.from(Instant.parse("2026-04-06T10:00:00Z")), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getValue().get("to"));
    assertEquals(rows, received);
  }

  @Test
  void streamLatestState_helperDomainPathStartsFromHelperDomainAndBindsSingleSource()
      throws SQLException {
    PreparedStatement statement = mockStreamingQueryExecution(List.of());
    var received = new ArrayList<HistoricalSourceEvent>();
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    reader.streamLatestState(
        PostGisHistoricalDeltaReader.StateDeltaReadPath.HELPER_DOMAIN,
        new HistoricalReadContext(List.of(sourceId), sourceId, true),
        chunk(),
        Instant.parse("2026-04-06T10:00:00Z"),
        received::add,
        5000);

    var sqlCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc)
        .execute(sqlCaptor.capture(), paramsCaptor.capture(), any(PreparedStatementCallback.class));
    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains("FROM geo.feature_latest_active_helper helper"));
    assertTrue(sql.contains("WHERE helper.source = :sourceId"));
    assertTrue(sql.contains("JOIN LATERAL"));
    assertTrue(sql.contains("features.object_id = helper.object_id"));
    assertTrue(sql.contains("COALESCE(features.start_at, features.created_at) >= :from"));
    assertTrue(sql.contains("COALESCE(features.start_at, features.created_at) < :to"));
    assertTrue(sql.contains("ORDER BY COALESCE(features.start_at, features.created_at) DESC"));
    assertTrue(sql.contains("features.created_at DESC"));
    assertTrue(sql.contains("features.id DESC"));
    assertTrue(sql.contains("JOIN geo.features payloads ON payloads.id = winners.id"));
    assertTrue(sql.contains("ORDER BY winners.playback_at ASC"));
    assertFalse(sql.contains("WITH changed_objects AS MATERIALIZED"));
    assertFalse(sql.contains("SELECT DISTINCT"));
    verify(statement).setFetchSize(5000);
    assertEquals(sourceId, paramsCaptor.getValue().get("sourceId"));
    assertEquals(
        Timestamp.from(Instant.parse("2026-04-06T10:00:00Z")), paramsCaptor.getValue().get("from"));
    assertEquals(Timestamp.from(chunk().to()), paramsCaptor.getValue().get("to"));
    assertFalse(paramsCaptor.getValue().containsKey("sourceIds"));
  }

  @Test
  void streamLatestState_helperDomainPathRejectsMultiSourceContext() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                reader.streamLatestState(
                    PostGisHistoricalDeltaReader.StateDeltaReadPath.HELPER_DOMAIN,
                    context(),
                    chunk(),
                    Instant.parse("2026-04-06T10:00:00Z"),
                    _ -> {},
                    5000));

    assertEquals(
        "Helper-domain state deltas require a single-source context", exception.getMessage());
    verify(jdbc, never()).execute(anyString(), anyMap(), any(PreparedStatementCallback.class));
  }

  private static HistoricalReadContext context() {
    return new HistoricalReadContext(
        List.of(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440011")),
        null,
        false);
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        0,
        "chunk-1",
        null,
        null,
        null,
        Instant.parse("2026-04-06T10:01:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"));
  }

  private static HistoricalSourceEvent event(String objectId, String playbackAt) {
    return new HistoricalSourceEvent(
        UUID.randomUUID(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440020"),
        objectId,
        Instant.parse(playbackAt),
        Instant.parse(playbackAt),
        null,
        null,
        "{}");
  }

  private PreparedStatement mockStreamingQueryExecution(List<HistoricalSourceEvent> rows) {
    try {
      PreparedStatement statement = preparedStreamingStatement(rows);
      when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
          .thenAnswer(
              invocation -> {
                @SuppressWarnings("unchecked")
                PreparedStatementCallback<Void> callback = invocation.getArgument(2);
                return callback.doInPreparedStatement(statement);
              });
      return statement;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private PreparedStatement preparedStreamingStatement(List<HistoricalSourceEvent> rows)
      throws SQLException {
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
    when(resultSet.getTimestamp("start_at")).thenReturn(null);
    when(resultSet.getTimestamp("stale_at")).thenReturn(null);
    when(resultSet.getString("payload_json"))
        .thenAnswer(_ -> rows.get(rowIndex.get()).payloadJson());

    when(statement.executeQuery()).thenReturn(resultSet);

    return statement;
  }
}
