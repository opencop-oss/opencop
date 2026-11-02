// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class NamedParameterJdbcExecuteStub {

  private NamedParameterJdbcExecuteStub() {}

  static List<PreparedStatement> stubExecuteRows(
      NamedParameterJdbcTemplate jdbc, List<?>... queryResults) {
    List<PreparedStatement> statements = new ArrayList<>();
    AtomicInteger queryIndex = new AtomicInteger();
    when(jdbc.execute(anyString(), anyMap(), any(PreparedStatementCallback.class)))
        .thenAnswer(
            invocation -> {
              PreparedStatement statement =
                  statementFor(queryResults[queryIndex.getAndIncrement()]);
              statements.add(statement);
              PreparedStatementCallback<?> callback = invocation.getArgument(2);
              return callback.doInPreparedStatement(statement);
            });
    return statements;
  }

  static Object residualRow(HistoricalSourceEvent event) {
    return new RowWithResidual(event, true);
  }

  private static PreparedStatement statementFor(List<?> rows) throws Exception {
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    AtomicInteger rowIndex = new AtomicInteger(-1);

    when(resultSet.next()).thenAnswer(_ -> rowIndex.incrementAndGet() < rows.size());
    when(resultSet.getObject("id", UUID.class)).thenAnswer(_ -> event(rows, rowIndex).eventId());
    when(resultSet.getObject("source", UUID.class))
        .thenAnswer(_ -> event(rows, rowIndex).sourceId());
    when(resultSet.getString("object_id"))
        .thenAnswer(
            _ -> {
              Object row = rows.get(rowIndex.get());
              return row instanceof String value ? value : event(rows, rowIndex).objectId();
            });
    when(resultSet.getTimestamp("playback_at"))
        .thenAnswer(_ -> Timestamp.from(event(rows, rowIndex).playbackAt()));
    when(resultSet.getTimestamp("created_at"))
        .thenAnswer(_ -> Timestamp.from(event(rows, rowIndex).createdAt()));
    when(resultSet.getTimestamp("start_at"))
        .thenAnswer(
            _ ->
                event(rows, rowIndex).startAt() == null
                    ? null
                    : Timestamp.from(event(rows, rowIndex).startAt()));
    when(resultSet.getTimestamp("stale_at"))
        .thenAnswer(
            _ ->
                event(rows, rowIndex).staleAt() == null
                    ? null
                    : Timestamp.from(event(rows, rowIndex).staleAt()));
    when(resultSet.getString("payload_json")).thenAnswer(_ -> event(rows, rowIndex).payloadJson());
    when(resultSet.getBoolean(PostGisHistoricalReadSql.IS_RESIDUAL_COLUMN))
        .thenAnswer(_ -> rows.get(rowIndex.get()) instanceof RowWithResidual row && row.residual());

    when(statement.executeQuery()).thenReturn(resultSet);
    return statement;
  }

  private static HistoricalSourceEvent event(List<?> rows, AtomicInteger rowIndex) {
    Object row = rows.get(rowIndex.get());
    return row instanceof RowWithResidual value ? value.event() : (HistoricalSourceEvent) row;
  }

  private record RowWithResidual(HistoricalSourceEvent event, boolean residual) {}
}
