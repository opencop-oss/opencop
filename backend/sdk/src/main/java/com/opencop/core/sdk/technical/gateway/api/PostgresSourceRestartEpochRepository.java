// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochReader;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Postgres-backed source restart epoch reader and writer.
 *
 * <p>JPA note: this adapter intentionally uses JDBC/native SQL for atomic {@code ON CONFLICT ...
 * RETURNING} epoch increments.
 */
public class PostgresSourceRestartEpochRepository
    implements SourceRestartEpochReader, SourceRestartEpochWriter {

  private static final String INCREMENT_SQL =
      """
      INSERT INTO profiles.socket_source_restart(source_id, restart_epoch)
      VALUES (:sourceId, 1)
      ON CONFLICT (source_id) DO UPDATE SET
        restart_epoch = profiles.socket_source_restart.restart_epoch + 1,
        updated_at = now()
      RETURNING restart_epoch
      """;

  private static final String FIND_SQL =
      """
      SELECT source_id, restart_epoch
        FROM profiles.socket_source_restart
       WHERE source_id IN (:sourceIds)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresSourceRestartEpochRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long incrementRestartEpoch(UUID sourceId) {
    return jdbc.queryForObject(INCREMENT_SQL, Map.of("sourceId", sourceId), Long.class);
  }

  @Override
  public Map<UUID, Long> findRestartEpochs(Set<UUID> sourceIds) {
    if (sourceIds == null || sourceIds.isEmpty()) {
      return Collections.emptyMap();
    }

    var params = new MapSqlParameterSource().addValue("sourceIds", sourceIds);
    return jdbc.query(
        FIND_SQL,
        params,
        rs -> {
          var epochs = new HashMap<UUID, Long>();
          while (rs.next()) {
            epochs.put(rs.getObject("source_id", UUID.class), rs.getLong("restart_epoch"));
          }
          return epochs;
        });
  }
}
