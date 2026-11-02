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
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class PostGisHistoricalDeltaReader {

  enum StateDeltaReadPath {
    CHANGED_OBJECT,
    HELPER_DOMAIN
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final int queryTimeoutSeconds;

  PostGisHistoricalDeltaReader(NamedParameterJdbcTemplate jdbc, Duration queryTimeout) {
    this.jdbc = jdbc;
    this.queryTimeoutSeconds = JdbcQueryTimeout.seconds(queryTimeout);
  }

  HistoricalReadPage read(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      HistoricalReadCursor cursor,
      int requestedPageSize) {
    Map<String, Object> params =
        paramsForPaging(
            context, previousChunkEndExclusive, chunk.to(), cursor, requestedPageSize + 1);
    var rows =
        TimedNamedParameterJdbcQuery.query(
            jdbc,
            PostGisHistoricalReadSql.deltaSql(cursor),
            params,
            (rs, rowNum) -> PostGisHistoricalReadRows.mapRow(rs),
            queryTimeoutSeconds);
    return PostGisHistoricalReadRows.page(rows, requestedPageSize);
  }

  void stream(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      Consumer<HistoricalSourceEvent> rowConsumer,
      int fetchSize) {
    var params = paramsForStreaming(context, previousChunkEndExclusive, chunk.to());

    jdbc.execute(
        PostGisHistoricalReadSql.streamDeltaSql(),
        params,
        (PreparedStatementCallback<Void>)
            preparedStatement -> {
              preparedStatement.setFetchSize(Math.max(1, fetchSize));
              preparedStatement.setQueryTimeout(queryTimeoutSeconds);
              try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                  rowConsumer.accept(PostGisHistoricalReadRows.mapRow(resultSet));
                }
              }
              return null;
            });
  }

  void streamLatestState(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      Consumer<HistoricalSourceEvent> rowConsumer,
      int fetchSize) {
    streamLatestState(
        StateDeltaReadPath.CHANGED_OBJECT,
        context,
        chunk,
        previousChunkEndExclusive,
        rowConsumer,
        fetchSize);
  }

  void streamLatestState(
      StateDeltaReadPath readPath,
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      Consumer<HistoricalSourceEvent> rowConsumer,
      int fetchSize) {
    var params = paramsForStreaming(readPath, context, previousChunkEndExclusive, chunk.to());

    jdbc.execute(
        stateDeltaSql(readPath),
        params,
        (PreparedStatementCallback<Void>)
            preparedStatement -> {
              preparedStatement.setFetchSize(Math.max(1, fetchSize));
              preparedStatement.setQueryTimeout(queryTimeoutSeconds);
              try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                  rowConsumer.accept(PostGisHistoricalReadRows.mapRow(resultSet));
                }
              }
              return null;
            });
  }

  private static String stateDeltaSql(StateDeltaReadPath readPath) {
    return switch (readPath) {
      case CHANGED_OBJECT -> PostGisHistoricalReadSql.streamStateDeltaSql();
      case HELPER_DOMAIN -> PostGisHistoricalReadSql.streamHelperDomainStateDeltaSql();
    };
  }

  private static Map<String, Object> params(
      HistoricalReadContext context,
      Instant from,
      Instant to,
      HistoricalReadCursor cursor,
      Integer limit,
      boolean includeCursor) {
    Map<String, Object> params = new HashMap<>();
    params.put("sourceIds", context.sourceIds());
    params.put("from", Timestamp.from(from));
    params.put("to", Timestamp.from(to));
    if (limit != null) {
      params.put("limit", limit);
    }

    if (includeCursor && cursor != null) {
      params.put("cursorPlaybackAt", Timestamp.from(cursor.playbackAt()));
      params.put("cursorObjectId", cursor.objectId());
      params.put("cursorEventId", cursor.eventId());
    }
    return params;
  }

  private static Map<String, Object> paramsForStreaming(
      HistoricalReadContext context, Instant from, Instant to) {
    return params(context, from, to, null, null, false);
  }

  private static Map<String, Object> paramsForStreaming(
      StateDeltaReadPath readPath, HistoricalReadContext context, Instant from, Instant to) {
    return switch (readPath) {
      case CHANGED_OBJECT -> paramsForStreaming(context, from, to);
      case HELPER_DOMAIN -> helperDomainParamsForStreaming(context, from, to);
    };
  }

  private static Map<String, Object> helperDomainParamsForStreaming(
      HistoricalReadContext context, Instant from, Instant to) {
    if (!context.isSingleSource() || context.singleSourceId() == null) {
      throw new IllegalArgumentException(
          "Helper-domain state deltas require a single-source context");
    }

    Map<String, Object> params = new HashMap<>();
    params.put("sourceId", context.singleSourceId());
    params.put("from", Timestamp.from(from));
    params.put("to", Timestamp.from(to));
    return params;
  }

  private static Map<String, Object> paramsForPaging(
      HistoricalReadContext context,
      Instant from,
      Instant to,
      HistoricalReadCursor cursor,
      int limit) {
    return params(context, from, to, cursor, limit, true);
  }
}
