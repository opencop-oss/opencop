// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalReadPage;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class PostGisHistoricalActiveSnapshotReader {

  private static final String CURSOR_PLAYBACK_AT_PARAM = "cursorPlaybackAt";
  private static final String CURSOR_OBJECT_ID_PARAM = "cursorObjectId";
  private static final String CURSOR_EVENT_ID_PARAM = "cursorEventId";

  private final NamedParameterJdbcTemplate jdbc;
  private final int queryTimeoutSeconds;

  PostGisHistoricalActiveSnapshotReader(NamedParameterJdbcTemplate jdbc, Duration queryTimeout) {
    this.jdbc = jdbc;
    this.queryTimeoutSeconds = JdbcQueryTimeout.seconds(queryTimeout);
  }

  HistoricalReadPage read(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize) {
    return readWithPath(context, chunk, cursor, requestedPageSize).page();
  }

  ActiveSnapshotReadResult readWithPath(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize) {
    if (context.singleSourceId() != null) {
      return readViaSingleSource(context, chunk, cursor, requestedPageSize);
    }

    List<HistoricalSourceEvent> rows =
        queryWinners(context.sourceIds(), chunk.from(), chunk.to(), cursor, requestedPageSize);
    return new ActiveSnapshotReadResult(
        PostGisHistoricalReadRows.page(rows, requestedPageSize), ActiveSnapshotReadPath.FULL);
  }

  private ActiveSnapshotReadResult readViaSingleSource(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int requestedPageSize) {
    List<HelperReadRow> rows =
        queryHelperWinners(
            context.singleSourceId(), chunk.from(), chunk.to(), cursor, requestedPageSize);
    List<HistoricalSourceEvent> events = rows.stream().map(HelperReadRow::row).toList();
    HistoricalReadPage page = PostGisHistoricalReadRows.page(events, requestedPageSize);
    boolean pageHasResidual =
        rows.stream().limit(page.rows().size()).anyMatch(HelperReadRow::residual);
    ActiveSnapshotReadPath path =
        pageHasResidual ? ActiveSnapshotReadPath.RESIDUAL : ActiveSnapshotReadPath.HELPER;
    return new ActiveSnapshotReadResult(page, path);
  }

  ActiveSnapshotReadPath streamSingleSourceViaHelperFastPath(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int fetchSize,
      Consumer<HistoricalSourceEvent> rowConsumer) {
    return streamSingleSourceViaHelperFastPath(
        context, chunk, cursor, fetchSize, (row, _) -> rowConsumer.accept(row));
  }

  ActiveSnapshotReadPath streamSingleSourceViaHelperFastPath(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      HistoricalReadCursor cursor,
      int fetchSize,
      BiConsumer<HistoricalSourceEvent, Boolean> rowConsumer) {
    Map<String, Object> params = new HashMap<>();
    params.put("sourceId", context.singleSourceId());
    params.put("from", Timestamp.from(chunk.from()));
    params.put("to", Timestamp.from(chunk.to()));
    if (cursor != null) {
      params.put(CURSOR_PLAYBACK_AT_PARAM, Timestamp.from(cursor.playbackAt()));
      params.put(CURSOR_OBJECT_ID_PARAM, cursor.objectId());
      params.put(CURSOR_EVENT_ID_PARAM, cursor.eventId());
    }

    final boolean[] hasResidual = new boolean[1];
    jdbc.execute(
        PostGisHistoricalReadSql.streamHelperWinnersSql(cursor),
        params,
        (PreparedStatementCallback<Void>)
            preparedStatement -> {
              preparedStatement.setFetchSize(Math.max(1, fetchSize));
              preparedStatement.setQueryTimeout(queryTimeoutSeconds);
              try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                  boolean residual =
                      resultSet.getBoolean(PostGisHistoricalReadSql.IS_RESIDUAL_COLUMN);
                  hasResidual[0] |= residual;
                  rowConsumer.accept(PostGisHistoricalReadRows.mapRow(resultSet), residual);
                }
              }
              return null;
            });

    return hasResidual[0] ? ActiveSnapshotReadPath.RESIDUAL : ActiveSnapshotReadPath.HELPER;
  }

  private List<HistoricalSourceEvent> queryWinners(
      List<UUID> sourceIds,
      Instant from,
      Instant to,
      HistoricalReadCursor cursor,
      int requestedPageSize) {
    Map<String, Object> params = buildParams(sourceIds, from, to, cursor, requestedPageSize + 1);
    return query(
        PostGisHistoricalReadSql.winnersSql(cursor),
        params,
        (rs, rowNum) -> PostGisHistoricalReadRows.mapRow(rs));
  }

  private List<HelperReadRow> queryHelperWinners(
      UUID sourceId, Instant from, Instant to, HistoricalReadCursor cursor, int requestedPageSize) {
    Map<String, Object> params = buildSingleSourceParams(sourceId, from, to, cursor);
    params.put("limit", requestedPageSize + 1);
    return query(
        PostGisHistoricalReadSql.helperWinnersSql(cursor),
        params,
        (rs, rowNum) ->
            new HelperReadRow(
                PostGisHistoricalReadRows.mapRow(rs),
                rs.getBoolean(PostGisHistoricalReadSql.IS_RESIDUAL_COLUMN)));
  }

  private <T> List<T> query(
      String sql,
      Map<String, Object> params,
      org.springframework.jdbc.core.RowMapper<T> rowMapper) {
    return TimedNamedParameterJdbcQuery.query(jdbc, sql, params, rowMapper, queryTimeoutSeconds);
  }

  private static Map<String, Object> buildParams(
      List<UUID> sourceIds, Instant from, Instant to, HistoricalReadCursor cursor, int limit) {
    Map<String, Object> params = new HashMap<>();
    params.put("sourceIds", sourceIds);
    params.put("from", Timestamp.from(from));
    params.put("to", Timestamp.from(to));
    params.put("limit", limit);
    if (cursor != null) {
      params.put(CURSOR_PLAYBACK_AT_PARAM, Timestamp.from(cursor.playbackAt()));
      params.put(CURSOR_OBJECT_ID_PARAM, cursor.objectId());
      params.put(CURSOR_EVENT_ID_PARAM, cursor.eventId());
    }
    return params;
  }

  private static Map<String, Object> buildSingleSourceParams(
      UUID sourceId, Instant from, Instant to, HistoricalReadCursor cursor) {
    Map<String, Object> params = new HashMap<>();
    params.put("sourceId", sourceId);
    params.put("from", Timestamp.from(from));
    params.put("to", Timestamp.from(to));
    if (cursor != null) {
      params.put(CURSOR_PLAYBACK_AT_PARAM, Timestamp.from(cursor.playbackAt()));
      params.put(CURSOR_OBJECT_ID_PARAM, cursor.objectId());
      params.put(CURSOR_EVENT_ID_PARAM, cursor.eventId());
    }
    return params;
  }

  enum ActiveSnapshotReadPath {
    HELPER,
    RESIDUAL,
    CHECKPOINT,
    FULL
  }

  record ActiveSnapshotReadResult(HistoricalReadPage page, ActiveSnapshotReadPath path) {}

  private record HelperReadRow(HistoricalSourceEvent row, boolean residual) {}
}
