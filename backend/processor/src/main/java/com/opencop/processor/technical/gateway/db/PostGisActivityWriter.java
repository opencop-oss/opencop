// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Records mapper and layer activity for a persistence batch, inside the caller's transaction.
 *
 * <p>Runs on the caller's {@link Connection} rather than opening its own, so features, helper rows
 * and activity commit or roll back together: a batch that fails must not leave a resource reporting
 * activity for events that were never stored.
 *
 * <p>Everything here is keyed per batch, not per message. A redelivery of the same records
 * recomputes the same batch key and conflicts, and the projection only ever moves forward, so the
 * coarse key costs nothing in the ordinary case: one row per mapper and per layer rather than one
 * per record, against a batch of up to {@code maxPollRecords}.
 *
 * <p>The exception is a remap. {@code sourceReceivedAt} is the raw record's own Kafka timestamp and
 * survives one, so layer delivery replays to the same instant and the projection ignores it. {@code
 * mappingCompletedAt} is taken at mapping time, so a remap republished at new downstream offsets
 * takes both a new batch key and a later completion time, and records a second mapper fact. That is
 * accepted rather than overlooked: the mapper did execute a second time, {@code effective_at} is
 * still clamped to {@code statement_timestamp()}, and the two executions are a retry backoff apart.
 * Keying by {@code FeatureProvenance#mappingExecutionId} would collapse them at the cost of one
 * ledger row per record on the persistence path.
 */
class PostGisActivityWriter {

  /**
   * One row per mapper in the batch, carrying that mapper's newest completion time.
   *
   * <p>{@code effective_at} is clamped on first acceptance only: a conflicting insert returns
   * nothing, so a replay neither recomputes the clamp nor advances anything.
   */
  private static final String MAPPING_EXECUTION_SQL =
      """
      INSERT INTO activity.recorded_activity (
        activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
      VALUES ('MAPPING_EXECUTION', ?, 'MAPPER', ?, ?, LEAST(?, statement_timestamp()))
          ON CONFLICT (activity_kind, activity_id, resource_kind, resource_id) DO NOTHING
      RETURNING resource_id, effective_at
      """;

  /**
   * One row per layer that can see any source in the batch, carrying the newest ingress time
   * reaching it.
   *
   * <p>The three membership paths are the structural ones only — no lifecycle, staleness or
   * viewport filter. A layer reached by more than one path is credited once, which is what the
   * {@code UNION} plus {@code GROUP BY} produce.
   *
   * <p>Membership is whatever this transaction's snapshot says, so a source attached after a batch
   * commits picks activity up from the next batch rather than that one. Replay semantics are
   * deliberately current-recipient rather than original-recipient: because {@code resource_id} is
   * part of the identity, a batch replayed after a membership change credits the newly attached
   * layer too. That needs a crash between the feature commit and the offset commit, and a
   * membership change inside the redelivery window; preserving the original recipient set would
   * mean persisting it per batch on this path. The layer can in fact see those features once
   * attached, so the credited instant is defensible.
   *
   * <p>The first branch is the feature's own source being a layer — a drawing, a broadcast, or a
   * clone target. It needs the {@code EXISTS} check because {@code geo.features.source} is
   * polymorphic and the caller does not separate the two kinds.
   */
  private static final String LAYER_DELIVERY_SQL =
      """
      WITH src AS (
        SELECT unnest(?::uuid[]) AS source_id, unnest(?::timestamptz[]) AS received_at
      ),
      visible AS (
        SELECT s.source_id AS layer_id, s.received_at
          FROM src s
         WHERE EXISTS (SELECT 1 FROM iam.layer WHERE id = s.source_id)
        UNION
        SELECT sla.layer_id, s.received_at
          FROM src s
          JOIN iam.server_layer_access sla ON sla.server_id = s.source_id
        UNION
        SELECT ldsg.layer_id, s.received_at
          FROM src s
          JOIN iam.data_source_group_data_source dsgds ON dsgds.data_source_id = s.source_id
          JOIN iam.layer_data_source_group ldsg
            ON ldsg.data_source_group_id = dsgds.data_source_group_id
      )
      INSERT INTO activity.recorded_activity (
        activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
      SELECT 'LAYER_DELIVERY', ?, 'LAYER', layer_id, MAX(received_at),
             LEAST(MAX(received_at), statement_timestamp())
        FROM visible
       GROUP BY layer_id
          ON CONFLICT (activity_kind, activity_id, resource_kind, resource_id) DO NOTHING
      RETURNING resource_id, effective_at
      """;

  private static final String PROJECTION_SQL =
      """
      INSERT INTO activity.resource_activity AS ra (resource_kind, resource_id, last_event_at)
      VALUES (?, ?, ?)
          ON CONFLICT (resource_kind, resource_id) DO UPDATE
         SET last_event_at = GREATEST(ra.last_event_at, EXCLUDED.last_event_at),
             updated_at    = clock_timestamp()
       WHERE ra.last_event_at < EXCLUDED.last_event_at
      """;

  private PostGisActivityWriter() {}

  /**
   * Records the batch's activity and advances the affected projections.
   *
   * <p>A null or blank {@code batchKey} records nothing: entry points outside the ingest pipeline
   * have no batch identity, and inventing one would let the same features be counted repeatedly.
   */
  static void record(Connection connection, String batchKey, List<PostGisFeatureRecord> records)
      throws SQLException {
    if (batchKey == null || batchKey.isBlank() || records.isEmpty()) {
      return;
    }

    var accepted = new ArrayList<AcceptedActivity>();
    accepted.addAll(recordMappingExecutions(connection, batchKey, records));
    accepted.addAll(recordLayerDeliveries(connection, batchKey, records));

    // Projections last, and in a stable global order. Two batches touching an overlapping set of
    // resources would otherwise be able to take the same row locks in opposite orders and deadlock;
    // the existing retry would recover, but ordering avoids paying for it.
    accepted.sort(
        Comparator.comparing(AcceptedActivity::resourceKind)
            .thenComparing(AcceptedActivity::resourceId));
    advanceProjections(connection, accepted);
  }

  private static List<AcceptedActivity> recordMappingExecutions(
      Connection connection, String batchKey, List<PostGisFeatureRecord> records)
      throws SQLException {
    var newestByMapper = new HashMap<UUID, Instant>();
    for (var record : records) {
      var provenance = record.provenance();
      if (provenance.mapperId() == null || provenance.mappingCompletedAt() == null) {
        continue;
      }
      newestByMapper.merge(
          provenance.mapperId(), provenance.mappingCompletedAt(), PostGisActivityWriter::newest);
    }
    if (newestByMapper.isEmpty()) {
      return List.of();
    }

    var accepted = new ArrayList<AcceptedActivity>(newestByMapper.size());
    try (var ps = connection.prepareStatement(MAPPING_EXECUTION_SQL)) {
      for (var entry : newestByMapper.entrySet()) {
        var reportedAt = Timestamp.from(entry.getValue());
        ps.setString(1, batchKey);
        ps.setObject(2, entry.getKey());
        ps.setTimestamp(3, reportedAt);
        ps.setTimestamp(4, reportedAt);
        try (var rs = ps.executeQuery()) {
          while (rs.next()) {
            accepted.add(
                new AcceptedActivity(
                    "MAPPER",
                    rs.getObject("resource_id", UUID.class),
                    rs.getTimestamp("effective_at").toInstant()));
          }
        }
      }
    }
    return accepted;
  }

  private static List<AcceptedActivity> recordLayerDeliveries(
      Connection connection, String batchKey, List<PostGisFeatureRecord> records)
      throws SQLException {
    var newestBySource = new HashMap<UUID, Instant>();
    for (var record : records) {
      var receivedAt = record.provenance().sourceReceivedAt();
      if (record.source() == null || receivedAt == null) {
        continue;
      }
      newestBySource.merge(record.source(), receivedAt, PostGisActivityWriter::newest);
    }
    if (newestBySource.isEmpty()) {
      return List.of();
    }

    var sourceIds = new UUID[newestBySource.size()];
    var receivedAts = new Timestamp[newestBySource.size()];
    var index = 0;
    for (var entry : newestBySource.entrySet()) {
      sourceIds[index] = entry.getKey();
      receivedAts[index] = Timestamp.from(entry.getValue());
      index++;
    }

    var accepted = new ArrayList<AcceptedActivity>();
    try (var ps = connection.prepareStatement(LAYER_DELIVERY_SQL)) {
      ps.setArray(1, connection.createArrayOf("uuid", sourceIds));
      ps.setArray(2, connection.createArrayOf("timestamptz", receivedAts));
      ps.setString(3, batchKey);
      try (var rs = ps.executeQuery()) {
        while (rs.next()) {
          accepted.add(
              new AcceptedActivity(
                  "LAYER",
                  rs.getObject("resource_id", UUID.class),
                  rs.getTimestamp("effective_at").toInstant()));
        }
      }
    }
    return accepted;
  }

  private static void advanceProjections(Connection connection, List<AcceptedActivity> accepted)
      throws SQLException {
    if (accepted.isEmpty()) {
      return;
    }
    try (var ps = connection.prepareStatement(PROJECTION_SQL)) {
      for (var activity : accepted) {
        ps.setString(1, activity.resourceKind());
        ps.setObject(2, activity.resourceId());
        ps.setTimestamp(3, Timestamp.from(activity.effectiveAt()));
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  private static Instant newest(Instant existing, Instant candidate) {
    return candidate.isAfter(existing) ? candidate : existing;
  }

  /** A happening the ledger accepted, and therefore the only kind that may advance a projection. */
  private record AcceptedActivity(String resourceKind, UUID resourceId, Instant effectiveAt) {}
}
