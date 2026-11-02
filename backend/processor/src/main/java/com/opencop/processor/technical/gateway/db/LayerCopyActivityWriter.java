// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Records an explicit copy into a layer — a clone or a merge — as that layer's activity.
 *
 * <p>Written from the caller rather than from inside {@code geo.clone_source_to_layer} and {@code
 * geo.merge_layers_ordered}. Both callers already own an explicit transaction around the procedure
 * call, so activity commits or rolls back with the copy either way, and re-declaring ~300 lines of
 * procedure body to append one insert would be transcription risk for no behavioral gain.
 *
 * <p>The copy operation is credited to the <em>target</em> layer only, and never to a mapper: a
 * copy moves features that some mapper produced earlier, which is not that mapper running again.
 * Historical {@code created_at} and the origin provenance on the copied rows are untouched.
 */
final class LayerCopyActivityWriter {

  /**
   * One statement so the projection can only be advanced by a row the ledger actually accepted.
   *
   * <p>{@code LEAST(..., statement_timestamp())} clamps the calling host's clock, as everywhere
   * else: the projection only moves forward, so an unclamped future instant would pin the layer.
   */
  private static final String SQL =
      """
      WITH accepted AS (
        INSERT INTO activity.recorded_activity (
          activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
        VALUES ('LAYER_COPY', ?, 'LAYER', ?, ?, LEAST(?, statement_timestamp()))
            ON CONFLICT (activity_kind, activity_id, resource_kind, resource_id) DO NOTHING
        RETURNING resource_id, effective_at
      )
      INSERT INTO activity.resource_activity AS ra (resource_kind, resource_id, last_event_at)
      SELECT 'LAYER', resource_id, effective_at FROM accepted
          ON CONFLICT (resource_kind, resource_id) DO UPDATE
         SET last_event_at = GREATEST(ra.last_event_at, EXCLUDED.last_event_at),
             updated_at    = clock_timestamp()
       WHERE ra.last_event_at < EXCLUDED.last_event_at
      """;

  private LayerCopyActivityWriter() {}

  /**
   * Records one copy operation against {@code targetLayerId}.
   *
   * <p>The operation id is generated per invocation rather than derived, because a copy is a
   * deliberate action rather than a replayable message: a retried copy is a new operation and
   * should read as one. Must be called inside the caller's copy transaction.
   */
  static void record(JdbcTemplate jdbc, UUID targetLayerId) {
    var copiedAt = Timestamp.from(Instant.now());
    jdbc.update(SQL, UUID.randomUUID().toString(), targetLayerId, copiedAt, copiedAt);
  }
}
