// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxAlertOutboxRow;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertOutboxRepository;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresWatchboxAlertOutboxRepository implements WatchboxAlertOutboxRepository {

  // JPA won't work here: the whole batch is one array-typed unnest statement, and this table
  // deliberately has no entity — rows are transient claim-and-delete freight, not domain state.
  // Same batch-unnest shape as the detection insert. Deliberately no ON CONFLICT: rows arrive
  // only for detections the database just accepted, so a unique-index conflict here is a violated
  // invariant to surface loudly, not input to tolerate. Plain child-table inserts: the only
  // parent locks are the FK KEY SHARE locks the detection insert already holds in this
  // transaction, so the generation-fence lock protocol is untouched.
  // The two handle columns ride along with the identity, never part of the claim fence or unique
  // index.
  private static final String INSERT_SQL =
      """
      INSERT INTO watchbox.watchbox_alert_outbox
        (watchbox_id, config_epoch, source_id, object_id, event_id, transition, event_time,
         detection_id, object_display_name)
      SELECT * FROM unnest(
        CAST(? AS uuid[]), CAST(? AS bigint[]), CAST(? AS uuid[]),
        CAST(? AS text[]), CAST(? AS uuid[]), CAST(? AS text[]), CAST(? AS timestamptz[]),
        CAST(? AS uuid[]), CAST(? AS text[]))
      AS pending(watchbox_id, config_epoch, source_id, object_id,
                 event_id, transition, event_time, detection_id, object_display_name)
      """;

  // JPA won't work here: this CTE claim atomically locks candidate rows, holds their parent
  // generations FOR SHARE, retires superseded epochs, and returns only current work. SKIP LOCKED
  // is the multi-drainer coordination: concurrent claimants partition the backlog instead of
  // blocking, and a claimant's crash rolls its claim and any stale-row retirement back.
  private static final String CLAIM_SQL =
      """
      WITH claimed AS MATERIALIZED (
        SELECT id, watchbox_id, config_epoch, source_id, object_id,
               event_id, transition, event_time, detection_id, object_display_name
        FROM watchbox.watchbox_alert_outbox
        ORDER BY id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      ), candidate_watchboxes AS MATERIALIZED (
        SELECT DISTINCT watchbox_id
        FROM claimed
      ), locked_watchboxes AS MATERIALIZED (
        SELECT current_watchbox.id, current_watchbox.config_epoch
        FROM watchbox.watchbox AS current_watchbox
        JOIN candidate_watchboxes
          ON candidate_watchboxes.watchbox_id = current_watchbox.id
        ORDER BY current_watchbox.id
        FOR SHARE OF current_watchbox
      ), retired AS (
        DELETE FROM watchbox.watchbox_alert_outbox AS outbox
        USING claimed, locked_watchboxes
        WHERE outbox.id = claimed.id
          AND locked_watchboxes.id = claimed.watchbox_id
          AND claimed.config_epoch <> locked_watchboxes.config_epoch
        RETURNING outbox.id
      )
      SELECT claimed.id, claimed.watchbox_id, claimed.config_epoch, claimed.source_id,
             claimed.object_id, claimed.event_id, claimed.transition, claimed.event_time,
             claimed.detection_id, claimed.object_display_name
      FROM claimed
      JOIN locked_watchboxes ON locked_watchboxes.id = claimed.watchbox_id
      WHERE claimed.config_epoch = locked_watchboxes.config_epoch
      ORDER BY claimed.id
      """;

  // JPA won't work here: no entity maps this table (see INSERT_SQL), so the array-bound bulk
  // delete of the claimed ids stays SQL.
  private static final String DELETE_SQL =
      """
      DELETE FROM watchbox.watchbox_alert_outbox WHERE id = ANY(CAST(? AS bigint[]))
      """;

  // JPA won't work here: no entity maps this table (see INSERT_SQL), so there is no repository
  // to derive findTopByOrderByIdAsc from.
  // id order stands in for created order (identity values are monotonic), keeping this O(1) on
  // the primary key even under a large backlog, where min(created_at) would scan.
  private static final String OLDEST_SQL =
      """
      SELECT created_at FROM watchbox.watchbox_alert_outbox ORDER BY id LIMIT 1
      """;

  private static final Comparator<AcceptedDetection> INSERT_ORDER =
      Comparator.comparing((AcceptedDetection accepted) -> accepted.transition().key().watchboxId())
          .thenComparingLong(accepted -> accepted.transition().key().configEpoch())
          .thenComparing(accepted -> accepted.transition().eventId())
          .thenComparing(accepted -> accepted.transition().kind());

  private final JdbcTemplate jdbc;

  public PostgresWatchboxAlertOutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insertAll(List<AcceptedDetection> acceptedDetections) {
    if (acceptedDetections.isEmpty()) {
      return;
    }
    var sorted = acceptedDetections.stream().sorted(INSERT_ORDER).toList();
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              insert(connection, sorted);
              return null;
            });
  }

  @Override
  public List<WatchboxAlertOutboxRow> claimBatch(int batchSize) {
    return jdbc.query(
        CLAIM_SQL,
        (resultSet, _) ->
            new WatchboxAlertOutboxRow(
                resultSet.getLong("id"),
                new WatchboxAlertEvent(
                    resultSet.getObject("watchbox_id", UUID.class),
                    resultSet.getLong("config_epoch"),
                    resultSet.getObject("source_id", UUID.class),
                    resultSet.getString("object_id"),
                    resultSet.getObject("event_id", UUID.class),
                    resultSet.getTimestamp("event_time").toInstant(),
                    WatchboxTransitionKind.valueOf(resultSet.getString("transition")),
                    resultSet.getObject("detection_id", UUID.class),
                    resultSet.getString("object_display_name"))),
        batchSize);
  }

  @Override
  public void deleteAll(List<Long> outboxIds) {
    if (outboxIds.isEmpty()) {
      return;
    }
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              var array = connection.createArrayOf("bigint", outboxIds.toArray());
              try {
                try (var statement = connection.prepareStatement(DELETE_SQL)) {
                  statement.setArray(1, array);
                  statement.executeUpdate();
                }
              } finally {
                array.free();
              }
              return null;
            });
  }

  @Override
  public Optional<Instant> oldestCreatedAt() {
    return jdbc
        .query(OLDEST_SQL, (resultSet, _) -> resultSet.getTimestamp("created_at").toInstant())
        .stream()
        .findFirst();
  }

  private static void insert(Connection connection, List<AcceptedDetection> detections)
      throws SQLException {
    var watchboxIds = new UUID[detections.size()];
    var configEpochs = new Long[detections.size()];
    var sourceIds = new UUID[detections.size()];
    var objectIds = new String[detections.size()];
    var eventIds = new UUID[detections.size()];
    var transitions = new String[detections.size()];
    var eventTimes = new Timestamp[detections.size()];
    var detectionIds = new UUID[detections.size()];
    var displayNames = new String[detections.size()];
    for (int index = 0; index < detections.size(); index++) {
      var accepted = detections.get(index);
      var detection = accepted.transition();
      detectionIds[index] = accepted.id();
      displayNames[index] = detection.objectDisplayName();
      watchboxIds[index] = detection.key().watchboxId();
      configEpochs[index] = detection.key().configEpoch();
      sourceIds[index] = detection.key().sourceId();
      objectIds[index] = detection.key().objectId();
      eventIds[index] = detection.eventId();
      transitions[index] = detection.kind().name();
      eventTimes[index] = Timestamp.from(detection.effectiveEventTime());
    }

    var arrays = new Array[9];
    SQLException operationFailure = null;
    try {
      arrays[0] = connection.createArrayOf("uuid", watchboxIds);
      arrays[1] = connection.createArrayOf("bigint", configEpochs);
      arrays[2] = connection.createArrayOf("uuid", sourceIds);
      arrays[3] = connection.createArrayOf("text", objectIds);
      arrays[4] = connection.createArrayOf("uuid", eventIds);
      arrays[5] = connection.createArrayOf("text", transitions);
      arrays[6] = connection.createArrayOf("timestamptz", eventTimes);
      arrays[7] = connection.createArrayOf("uuid", detectionIds);
      arrays[8] = connection.createArrayOf("text", displayNames);
      try (var statement = connection.prepareStatement(INSERT_SQL)) {
        for (int index = 0; index < arrays.length; index++) {
          statement.setArray(index + 1, arrays[index]);
        }
        statement.executeUpdate();
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
}
