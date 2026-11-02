// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import com.opencop.core.sdk.ports.gateway.audit.LastEventAtWriter;
import com.opencop.core.sdk.technical.gateway.audit.LastEventAtCoalescer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Records an operator opening an event detail as layer activity, in {@code
 * activity.resource_activity}.
 *
 * <p>Callers supply an event's source, which may be a layer <em>or</em> a data source. The {@code
 * EXISTS} guard is what discards the latter: the previous version of this writer updated {@code
 * iam.layer} directly and relied on a data-source id matching no row, but an upsert has no such
 * property and would happily create a row keyed {@code (LAYER, <data-source-id>)}. Because {@code
 * resource_id} is polymorphic and carries no foreign key, the database cannot reject that either,
 * and the junk row would be invisible to every read — layer reads are driven outward from {@code
 * iam.layer} — so it would accumulate silently. Discarding is the correct outcome rather than an
 * error to report: if the event belongs to a data source, that source's activity is already tracked
 * by the runtime-observation trigger.
 *
 * <p>{@code LEAST(..., statement_timestamp())} clamps the calling host's clock. The projection only
 * ever moves forward, so an unclamped future instant from a skewed Core instance would pin the
 * layer permanently, with every later correct write rejected as older.
 */
public class PostgresLayerLastEventWriter implements LastEventAtWriter {

  private static final String SQL =
      """
      WITH accepted AS (
        INSERT INTO activity.recorded_activity (
          activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
        SELECT 'LAYER_EVENT_VIEW', :activityId, 'LAYER', :id, :observedAt,
               LEAST(:observedAt, statement_timestamp())
         WHERE EXISTS (SELECT 1 FROM iam.layer WHERE id = :id)
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

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresLayerLastEventWriter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void write(UUID resourceId, Instant observedAt) {
    jdbc.update(
        SQL,
        new MapSqlParameterSource()
            .addValue("id", resourceId)
            .addValue("activityId", activityId(resourceId, observedAt))
            .addValue("observedAt", Timestamp.from(observedAt)));
  }

  /**
   * Identity of one viewing episode: the layer plus the flush interval the view fell in.
   *
   * <p>A click has no natural identity — the detail panel polls every 10s while it stays open, and
   * the coalescer already collapses that to one write per layer per interval. Bucketing by the same
   * interval gives those writes a stable identity, so a flush that fails and retries carries the
   * same pending instant, lands in the same bucket, and records once rather than twice.
   *
   * <p>Derived from {@link LastEventAtCoalescer#DEFAULT_FLUSH_INTERVAL} rather than a literal so
   * the bucket cannot drift away from the cadence that produces the writes.
   */
  private static String activityId(UUID resourceId, Instant observedAt) {
    var bucketSeconds = Math.max(1L, LastEventAtCoalescer.DEFAULT_FLUSH_INTERVAL.toSeconds());
    return resourceId + ":" + (observedAt.getEpochSecond() / bucketSeconds);
  }
}
