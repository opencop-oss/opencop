// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class TimedNamedParameterJdbcQuery {

  private TimedNamedParameterJdbcQuery() {}

  static <T> List<T> query(
      NamedParameterJdbcTemplate jdbc,
      String sql,
      Map<String, Object> params,
      RowMapper<T> rowMapper,
      int queryTimeoutSeconds) {
    return jdbc.execute(
        sql,
        params,
        (PreparedStatementCallback<List<T>>)
            preparedStatement -> {
              preparedStatement.setQueryTimeout(queryTimeoutSeconds);
              try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return mapRows(resultSet, rowMapper);
              }
            });
  }

  private static <T> List<T> mapRows(ResultSet resultSet, RowMapper<T> rowMapper)
      throws java.sql.SQLException {
    var rows = new java.util.ArrayList<T>();
    int rowNum = 0;
    while (resultSet.next()) {
      rows.add(rowMapper.mapRow(resultSet, rowNum++));
    }
    return rows;
  }
}
