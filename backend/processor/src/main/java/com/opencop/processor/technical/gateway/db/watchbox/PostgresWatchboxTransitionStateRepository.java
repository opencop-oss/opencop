// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxTransitionStateRepository;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresWatchboxTransitionStateRepository
    implements WatchboxTransitionStateRepository {

  // JPA won't work here: INSERT ... ON CONFLICT upsert keyed on the composite fold key
  // has no JPA equivalent.
  private static final String UPSERT_SQL =
      """
      INSERT INTO watchbox.watchbox_transition_state
        (watchbox_id, config_epoch, source_id, object_id, last_inside,
         last_geometry_inside, last_event_id, last_effective_event_time, last_observed_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (watchbox_id, config_epoch, source_id, object_id) DO UPDATE SET
        last_inside = EXCLUDED.last_inside,
        last_geometry_inside = EXCLUDED.last_geometry_inside,
        last_event_id = EXCLUDED.last_event_id,
        last_effective_event_time = EXCLUDED.last_effective_event_time,
        last_observed_at = EXCLUDED.last_observed_at,
        updated_at = now()
      """;

  // JPA won't work here: array-typed unnest batch insert with ON CONFLICT DO NOTHING
  // and RETURNING to report which rows were actually seeded.
  private static final String INSERT_ABSENT_SQL =
      """
      WITH candidates AS (
        SELECT *
        FROM unnest(
          CAST(? AS uuid[]), CAST(? AS bigint[]), CAST(? AS uuid[]),
          CAST(? AS text[]), CAST(? AS boolean[]), CAST(? AS boolean[]),
          CAST(? AS uuid[]), CAST(? AS timestamptz[]), CAST(? AS timestamptz[]))
        AS candidate(watchbox_id, config_epoch, source_id, object_id,
                     last_inside, last_geometry_inside, last_event_id,
                     last_effective_event_time, last_observed_at)
      )
      INSERT INTO watchbox.watchbox_transition_state
        (watchbox_id, config_epoch, source_id, object_id, last_inside,
         last_geometry_inside, last_event_id, last_effective_event_time, last_observed_at)
      SELECT * FROM candidates
      ON CONFLICT (watchbox_id, config_epoch, source_id, object_id) DO NOTHING
      RETURNING watchbox_id, config_epoch, source_id, object_id
      """;

  // JPA won't work here: a bounded-batch DELETE needs the ORDER BY/LIMIT subselect plus the
  // outer recheck below.
  // The outer last_observed_at guard re-runs during the delete's row recheck after a lock
  // wait, so a row reseeded by a concurrent batch survives; the subquery alone would not
  // re-evaluate and would delete the freshly refreshed row.
  private static final String DELETE_EXPIRED_SQL =
      """
      DELETE FROM watchbox.watchbox_transition_state
      WHERE (watchbox_id, config_epoch, source_id, object_id) IN (
        SELECT watchbox_id, config_epoch, source_id, object_id
        FROM watchbox.watchbox_transition_state
        WHERE last_observed_at < ?
        ORDER BY last_observed_at, watchbox_id, config_epoch, source_id, object_id
        LIMIT ?)
        AND last_observed_at < ?
      """;

  // JPA won't work here: the batch must lock each requested parent generation FOR SHARE before
  // locking matching state rows FOR UPDATE, both in deterministic order. The parent lock is the
  // generation fence: a concurrent config-epoch update linearizes before this transaction (and
  // makes it retry with fresh config) or waits until all state/status/detection writes commit.
  private static final String LOCK_CURRENT_AND_SELECT_FOR_UPDATE_SQL =
      """
      WITH requested_keys AS MATERIALIZED (
        SELECT *
        FROM unnest(
          CAST(? AS uuid[]), CAST(? AS bigint[]), CAST(? AS uuid[]), CAST(? AS text[]))
        AS requested(watchbox_id, config_epoch, source_id, object_id)
      ),
      requested_generations AS MATERIALIZED (
        SELECT DISTINCT watchbox_id, config_epoch
        FROM requested_keys
      ),
      locked_watchboxes AS MATERIALIZED (
        SELECT w.id, w.config_epoch
        FROM watchbox.watchbox w
        JOIN requested_generations requested
          ON requested.watchbox_id = w.id
         AND requested.config_epoch = w.config_epoch
        ORDER BY w.id, w.config_epoch
        FOR SHARE OF w
      ),
      locked_states AS MATERIALIZED (
        SELECT s.watchbox_id, s.config_epoch, s.source_id, s.object_id, s.last_inside,
               s.last_geometry_inside, s.last_event_id, s.last_effective_event_time,
               s.last_observed_at
        FROM watchbox.watchbox_transition_state s
        JOIN requested_keys requested
          ON requested.watchbox_id = s.watchbox_id
         AND requested.config_epoch = s.config_epoch
         AND requested.source_id = s.source_id
         AND requested.object_id = s.object_id
        JOIN locked_watchboxes w
          ON w.id = s.watchbox_id
         AND w.config_epoch = s.config_epoch
        ORDER BY s.watchbox_id, s.config_epoch, s.source_id, s.object_id
        FOR UPDATE OF s
      )
      SELECT w.id AS locked_watchbox_id,
             w.config_epoch AS locked_config_epoch,
             NULL::uuid AS watchbox_id,
             NULL::bigint AS config_epoch,
             NULL::uuid AS source_id,
             NULL::text AS object_id,
             NULL::boolean AS last_inside,
             NULL::boolean AS last_geometry_inside,
             NULL::uuid AS last_event_id,
             NULL::timestamptz AS last_effective_event_time,
             NULL::timestamptz AS last_observed_at
      FROM locked_watchboxes w
      UNION ALL
      SELECT s.watchbox_id AS locked_watchbox_id,
             s.config_epoch AS locked_config_epoch,
             s.watchbox_id, s.config_epoch, s.source_id, s.object_id, s.last_inside,
             s.last_geometry_inside, s.last_event_id, s.last_effective_event_time,
             s.last_observed_at
      FROM locked_states s
      """;

  private final JdbcTemplate jdbc;

  public PostgresWatchboxTransitionStateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public WatchboxStateLockResult lockCurrentAndSelectForUpdate(Collection<WatchboxStateKey> keys) {
    if (keys.isEmpty()) {
      return new WatchboxStateLockResult.Current(Map.of());
    }
    var sortedKeys = keys.stream().distinct().sorted().toList();
    return jdbc.execute(
        (ConnectionCallback<WatchboxStateLockResult>)
            connection -> lockCurrentAndSelectForUpdate(connection, sortedKeys));
  }

  @Override
  public void upsertAll(List<WatchboxStateRow> rows) {
    batchUpdate(UPSERT_SQL, rows);
  }

  @Override
  public Set<WatchboxStateKey> insertAbsent(List<WatchboxStateRow> rows) {
    if (rows.isEmpty()) {
      return Set.of();
    }
    var sortedRows = rows.stream().sorted(Comparator.comparing(WatchboxStateRow::key)).toList();
    return jdbc.execute(
        (ConnectionCallback<Set<WatchboxStateKey>>)
            connection -> insertAbsent(connection, sortedRows));
  }

  @Override
  public int deleteExpiredBatch(Instant cutoff, int batchSize) {
    var cutoffTimestamp = Timestamp.from(cutoff);
    return jdbc.update(DELETE_EXPIRED_SQL, cutoffTimestamp, batchSize, cutoffTimestamp);
  }

  private static WatchboxStateLockResult lockCurrentAndSelectForUpdate(
      Connection connection, List<WatchboxStateKey> keys) throws SQLException {
    var watchboxIds = new UUID[keys.size()];
    var configEpochs = new Long[keys.size()];
    var sourceIds = new UUID[keys.size()];
    var objectIds = new String[keys.size()];
    var requestedGenerations = new HashSet<WatchboxGeneration>();
    for (int index = 0; index < keys.size(); index++) {
      var key = keys.get(index);
      watchboxIds[index] = key.watchboxId();
      configEpochs[index] = key.configEpoch();
      sourceIds[index] = key.sourceId();
      objectIds[index] = key.objectId();
      requestedGenerations.add(new WatchboxGeneration(key.watchboxId(), key.configEpoch()));
    }

    var arrays = new Array[4];
    SQLException operationFailure = null;
    try {
      arrays[0] = connection.createArrayOf("uuid", watchboxIds);
      arrays[1] = connection.createArrayOf("bigint", configEpochs);
      arrays[2] = connection.createArrayOf("uuid", sourceIds);
      arrays[3] = connection.createArrayOf("text", objectIds);
      try (var statement = connection.prepareStatement(LOCK_CURRENT_AND_SELECT_FOR_UPDATE_SQL)) {
        for (int index = 0; index < arrays.length; index++) {
          statement.setArray(index + 1, arrays[index]);
        }
        try (var resultSet = statement.executeQuery()) {
          var lockedGenerations = new HashSet<WatchboxGeneration>();
          Map<WatchboxStateKey, WatchboxStateRow> byKey = new LinkedHashMap<>();
          while (resultSet.next()) {
            lockedGenerations.add(
                new WatchboxGeneration(
                    resultSet.getObject("locked_watchbox_id", UUID.class),
                    resultSet.getLong("locked_config_epoch")));
            var watchboxId = resultSet.getObject("watchbox_id", UUID.class);
            if (watchboxId == null) {
              continue;
            }
            var row =
                new WatchboxStateRow(
                    new WatchboxStateKey(
                        watchboxId,
                        resultSet.getLong("config_epoch"),
                        resultSet.getObject("source_id", UUID.class),
                        resultSet.getString("object_id")),
                    resultSet.getBoolean("last_inside"),
                    resultSet.getBoolean("last_geometry_inside"),
                    resultSet.getObject("last_event_id", UUID.class),
                    resultSet.getTimestamp("last_effective_event_time").toInstant(),
                    resultSet.getTimestamp("last_observed_at").toInstant());
            byKey.put(row.key(), row);
          }
          if (!lockedGenerations.equals(requestedGenerations)) {
            return new WatchboxStateLockResult.GenerationChanged();
          }
          return new WatchboxStateLockResult.Current(byKey);
        }
      }
    } catch (SQLException exception) {
      operationFailure = exception;
      throw exception;
    } finally {
      try {
        free(arrays);
      } catch (SQLException cleanupFailure) {
        if (operationFailure != null) {
          operationFailure.addSuppressed(cleanupFailure);
        } else {
          throw cleanupFailure;
        }
      }
    }
  }

  private int[] batchUpdate(String sql, List<WatchboxStateRow> input) {
    if (input.isEmpty()) {
      return new int[0];
    }
    var rows = input.stream().sorted(Comparator.comparing(WatchboxStateRow::key)).toList();
    return jdbc.batchUpdate(
        sql,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int index) throws SQLException {
            bind(statement, rows.get(index));
          }

          @Override
          public int getBatchSize() {
            return rows.size();
          }
        });
  }

  private static Set<WatchboxStateKey> insertAbsent(
      Connection connection, List<WatchboxStateRow> rows) throws SQLException {
    var watchboxIds = new UUID[rows.size()];
    var configEpochs = new Long[rows.size()];
    var sourceIds = new UUID[rows.size()];
    var objectIds = new String[rows.size()];
    var lastInside = new Boolean[rows.size()];
    var lastGeometryInside = new Boolean[rows.size()];
    var lastEventIds = new UUID[rows.size()];
    var lastEffectiveEventTimes = new Timestamp[rows.size()];
    var lastObservedAt = new Timestamp[rows.size()];
    for (int index = 0; index < rows.size(); index++) {
      var row = rows.get(index);
      watchboxIds[index] = row.key().watchboxId();
      configEpochs[index] = row.key().configEpoch();
      sourceIds[index] = row.key().sourceId();
      objectIds[index] = row.key().objectId();
      lastInside[index] = row.lastInside();
      lastGeometryInside[index] = row.lastGeometryInside();
      lastEventIds[index] = row.lastEventId();
      lastEffectiveEventTimes[index] = Timestamp.from(row.lastEffectiveEventTime());
      lastObservedAt[index] = Timestamp.from(row.lastObservedAt());
    }

    var arrays = new Array[9];
    SQLException operationFailure = null;
    try {
      arrays[0] = connection.createArrayOf("uuid", watchboxIds);
      arrays[1] = connection.createArrayOf("bigint", configEpochs);
      arrays[2] = connection.createArrayOf("uuid", sourceIds);
      arrays[3] = connection.createArrayOf("text", objectIds);
      arrays[4] = connection.createArrayOf("boolean", lastInside);
      arrays[5] = connection.createArrayOf("boolean", lastGeometryInside);
      arrays[6] = connection.createArrayOf("uuid", lastEventIds);
      arrays[7] = connection.createArrayOf("timestamptz", lastEffectiveEventTimes);
      arrays[8] = connection.createArrayOf("timestamptz", lastObservedAt);
      try (var statement = connection.prepareStatement(INSERT_ABSENT_SQL)) {
        for (int index = 0; index < arrays.length; index++) {
          statement.setArray(index + 1, arrays[index]);
        }
        try (var resultSet = statement.executeQuery()) {
          var insertedKeys = new LinkedHashSet<WatchboxStateKey>();
          while (resultSet.next()) {
            insertedKeys.add(
                new WatchboxStateKey(
                    resultSet.getObject("watchbox_id", UUID.class),
                    resultSet.getLong("config_epoch"),
                    resultSet.getObject("source_id", UUID.class),
                    resultSet.getString("object_id")));
          }
          return Set.copyOf(insertedKeys);
        }
      }
    } catch (SQLException exception) {
      operationFailure = exception;
      throw exception;
    } finally {
      try {
        free(arrays);
      } catch (SQLException cleanupFailure) {
        if (operationFailure != null) {
          operationFailure.addSuppressed(cleanupFailure);
        } else {
          throw cleanupFailure;
        }
      }
    }
  }

  private static void free(Array[] arrays) throws SQLException {
    SQLException firstFailure = null;
    for (var array : arrays) {
      if (array == null) {
        continue;
      }
      try {
        array.free();
      } catch (SQLException exception) {
        if (firstFailure == null) {
          firstFailure = exception;
        } else {
          firstFailure.addSuppressed(exception);
        }
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
  }

  private static void bind(PreparedStatement statement, WatchboxStateRow row) throws SQLException {
    statement.setObject(1, row.key().watchboxId());
    statement.setLong(2, row.key().configEpoch());
    statement.setObject(3, row.key().sourceId());
    statement.setString(4, row.key().objectId());
    statement.setBoolean(5, row.lastInside());
    statement.setBoolean(6, row.lastGeometryInside());
    statement.setObject(7, row.lastEventId());
    statement.setTimestamp(8, Timestamp.from(row.lastEffectiveEventTime()));
    statement.setTimestamp(9, Timestamp.from(row.lastObservedAt()));
  }

  private record WatchboxGeneration(UUID watchboxId, long configEpoch) {}
}
