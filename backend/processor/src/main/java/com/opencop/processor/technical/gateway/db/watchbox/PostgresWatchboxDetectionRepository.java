// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxDetectionRepository;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresWatchboxDetectionRepository implements WatchboxDetectionRepository {
  // JPA won't work here: the whole batch is one array-typed unnest statement, and
  // ON CONFLICT DO NOTHING is the replay-idempotency contract — neither has a JPA mapping.
  // RETURNING yields only the rows this statement inserted, so the caller learns which
  // candidates the database accepted as novel (same shape as the state repo's insertAbsent).
  private static final String INSERT_SQL =
      """
      INSERT INTO watchbox.watchbox_detection
        (watchbox_id, config_epoch, source_id, object_id, event_id,
         transition, event_time, object_display_name)
      SELECT * FROM unnest(
        CAST(? AS uuid[]), CAST(? AS bigint[]), CAST(? AS uuid[]),
        CAST(? AS text[]), CAST(? AS uuid[]), CAST(? AS text[]),
        CAST(? AS timestamptz[]), CAST(? AS text[]))
      AS candidate(watchbox_id, config_epoch, source_id, object_id,
                   event_id, transition, event_time, object_display_name)
      ON CONFLICT
        (watchbox_id, config_epoch, source_id, object_id, event_id, transition)
      DO NOTHING
      RETURNING id, watchbox_id, config_epoch, source_id, object_id, event_id, transition
      """;

  // JPA won't work here: a bounded-batch DELETE needs the ORDER BY/LIMIT subselect.
  // Detections are immutable facts, so unlike the transition-state delete there is no
  // re-observed row to protect with an outer recheck: an id selected under the cutoff
  // stays under the cutoff.
  private static final String DELETE_EXPIRED_SQL =
      """
      DELETE FROM watchbox.watchbox_detection
      WHERE id IN (
        SELECT id
        FROM watchbox.watchbox_detection
        WHERE event_time < ?
        ORDER BY event_time, id
        LIMIT ?)
      """;

  private static final Comparator<WatchboxTransition> INSERT_ORDER =
      Comparator.comparing((WatchboxTransition transition) -> transition.key().watchboxId())
          .thenComparingLong(transition -> transition.key().configEpoch())
          .thenComparing(WatchboxTransition::eventId)
          .thenComparing(WatchboxTransition::kind);

  private final JdbcTemplate jdbc;

  public PostgresWatchboxDetectionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AcceptedDetection> write(List<WatchboxTransition> emissions) {
    if (emissions.isEmpty()) {
      return List.of();
    }
    var sorted = emissions.stream().sorted(INSERT_ORDER).toList();
    return jdbc.execute(
        (ConnectionCallback<List<AcceptedDetection>>) connection -> insert(connection, sorted));
  }

  @Override
  public int deleteExpiredBatch(Instant cutoff, int batchSize) {
    return jdbc.update(DELETE_EXPIRED_SQL, Timestamp.from(cutoff), batchSize);
  }

  private static List<AcceptedDetection> insert(
      Connection connection, List<WatchboxTransition> emissions) throws SQLException {
    // An intra-batch crafted duplicate collapses to one key; either occurrence is a valid
    // representative of the single row the database inserts for it.
    var byIdentity = new HashMap<DetectionIdentity, WatchboxTransition>();
    for (var emission : emissions) {
      byIdentity.putIfAbsent(DetectionIdentity.of(emission), emission);
    }
    var watchboxIds = new UUID[emissions.size()];
    var configEpochs = new Long[emissions.size()];
    var sourceIds = new UUID[emissions.size()];
    var objectIds = new String[emissions.size()];
    var eventIds = new UUID[emissions.size()];
    var transitions = new String[emissions.size()];
    var eventTimes = new Timestamp[emissions.size()];
    var displayNames = new String[emissions.size()];
    for (int index = 0; index < emissions.size(); index++) {
      var emission = emissions.get(index);
      watchboxIds[index] = emission.key().watchboxId();
      configEpochs[index] = emission.key().configEpoch();
      sourceIds[index] = emission.key().sourceId();
      objectIds[index] = emission.key().objectId();
      eventIds[index] = emission.eventId();
      transitions[index] = emission.kind().name();
      eventTimes[index] = Timestamp.from(emission.effectiveEventTime());
      displayNames[index] = emission.objectDisplayName();
    }

    var arrays = new Array[8];
    SQLException operationFailure = null;
    try {
      arrays[0] = connection.createArrayOf("uuid", watchboxIds);
      arrays[1] = connection.createArrayOf("bigint", configEpochs);
      arrays[2] = connection.createArrayOf("uuid", sourceIds);
      arrays[3] = connection.createArrayOf("text", objectIds);
      arrays[4] = connection.createArrayOf("uuid", eventIds);
      arrays[5] = connection.createArrayOf("text", transitions);
      arrays[6] = connection.createArrayOf("timestamptz", eventTimes);
      arrays[7] = connection.createArrayOf("text", displayNames);
      try (var statement = connection.prepareStatement(INSERT_SQL)) {
        for (int index = 0; index < arrays.length; index++) {
          statement.setArray(index + 1, arrays[index]);
        }
        try (var resultSet = statement.executeQuery()) {
          var accepted = new ArrayList<AcceptedDetection>();
          while (resultSet.next()) {
            var identity =
                new DetectionIdentity(
                    resultSet.getObject("watchbox_id", UUID.class),
                    resultSet.getLong("config_epoch"),
                    resultSet.getObject("source_id", UUID.class),
                    resultSet.getString("object_id"),
                    resultSet.getObject("event_id", UUID.class),
                    WatchboxTransitionKind.valueOf(resultSet.getString("transition")));
            var emission = byIdentity.get(identity);
            if (emission == null) {
              throw new IllegalStateException(
                  "detection insert returned a row this batch never submitted: " + identity);
            }
            accepted.add(new AcceptedDetection(resultSet.getObject("id", UUID.class), emission));
          }
          return List.copyOf(accepted);
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

  /** The unique detection identity (V37's watchbox_detection_event_uniq), as returned rows. */
  private record DetectionIdentity(
      UUID watchboxId,
      long configEpoch,
      UUID sourceId,
      String objectId,
      UUID eventId,
      WatchboxTransitionKind kind) {

    private static DetectionIdentity of(WatchboxTransition emission) {
      return new DetectionIdentity(
          emission.key().watchboxId(),
          emission.key().configEpoch(),
          emission.key().sourceId(),
          emission.key().objectId(),
          emission.eventId(),
          emission.kind());
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
