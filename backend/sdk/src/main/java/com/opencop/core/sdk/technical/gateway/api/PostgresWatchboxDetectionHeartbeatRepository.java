// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Postgres-backed watchbox-detection heartbeat writer and liveness reader.
 *
 * <p>Like {@link PostgresConnectWorkerHealthRepository}, this adapter is an intentional exception
 * to the JPA convention for ephemeral Postgres-only control-plane rows where the SQL contract is
 * the behavior: TTL upserts and expiry interval math would not become more domain-oriented behind
 * an entity.
 *
 * <p>The upsert deliberately clears {@code stopped_at}: in Docker the instance id is the container
 * hostname, stable across restarts, so a restarted Processor must be able to revive the row its
 * previous run marked stopped. Guarding against the shutdown race (a straggler flush landing after
 * {@link #markStopped}) is therefore the publisher's job - it must make the stop marker its final
 * write - because this layer cannot tell a dying process's late flush from a restarted process's
 * first heartbeat.
 *
 * <p>Reads deliberately do not catch here: the Core-side caller owns the fail-closed decision, and
 * swallowing a failure into {@code false} at this layer would hide the distinction between "read
 * says dead" and "read failed" from it.
 */
public class PostgresWatchboxDetectionHeartbeatRepository
    implements WatchboxDetectionHeartbeatWriter, WatchboxDetectionLivenessReader {

  // Instance ids are per-container, so every Processor recreate leaves a dead row behind; a killed
  // process leaves one that never even got its stopped_at. One day of history is plenty for a
  // postmortem, and reaping on the write path keeps the table self-maintaining without a
  // scheduler: the writer that adds rows is the one that trims them.
  private static final Duration DEAD_ROW_RETENTION = Duration.ofDays(1);

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresWatchboxDetectionHeartbeatRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void writeHeartbeat(WatchboxDetectionHeartbeat heartbeat, Duration ttl) {
    var retainedRetry = heartbeat.retainedRetry();
    // JPA won't work here: INSERT ... ON CONFLICT upsert keyed on instance_id has no JPA
    // equivalent, and a load-then-save would race concurrent heartbeat writers.
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat (
          instance_id,
          listener_started_at,
          last_heartbeat_at,
          heartbeat_expires_at,
          last_evaluated_at,
          assigned_partitions,
          heartbeat_sequence,
          stopped_at,
          retained_retry_partitions,
          retained_retry_reason,
          retained_retry_since,
          retained_retry_attempts
        )
        VALUES (
          :instanceId,
          :listenerStartedAt,
          now(),
          now() + (:ttlMillis * INTERVAL '1 millisecond'),
          :lastEvaluatedAt,
          :assignedPartitions,
          :heartbeatSequence,
          NULL,
          :retainedRetryPartitions,
          :retainedRetryReason,
          :retainedRetrySince,
          :retainedRetryAttempts
        )
        ON CONFLICT (instance_id) DO UPDATE SET
          listener_started_at = EXCLUDED.listener_started_at,
          last_heartbeat_at = now(),
          heartbeat_expires_at = now() + (:ttlMillis * INTERVAL '1 millisecond'),
          last_evaluated_at = EXCLUDED.last_evaluated_at,
          assigned_partitions = EXCLUDED.assigned_partitions,
          heartbeat_sequence = EXCLUDED.heartbeat_sequence,
          stopped_at = NULL,
          retained_retry_partitions = EXCLUDED.retained_retry_partitions,
          retained_retry_reason = EXCLUDED.retained_retry_reason,
          retained_retry_since = EXCLUDED.retained_retry_since,
          retained_retry_attempts = EXCLUDED.retained_retry_attempts,
          updated_at = now()
        """,
        new MapSqlParameterSource()
            .addValue("instanceId", heartbeat.instanceId())
            .addValue("listenerStartedAt", Timestamp.from(heartbeat.listenerStartedAt()))
            .addValue(
                "lastEvaluatedAt",
                heartbeat.lastEvaluatedAt() == null
                    ? null
                    : Timestamp.from(heartbeat.lastEvaluatedAt()))
            .addValue("assignedPartitions", heartbeat.assignedPartitions())
            .addValue("heartbeatSequence", heartbeat.heartbeatSequence())
            .addValue(
                "retainedRetryPartitions",
                retainedRetry == null ? null : retainedRetry.partitions())
            .addValue(
                "retainedRetryReason", retainedRetry == null ? null : retainedRetry.reason().name())
            .addValue(
                "retainedRetrySince",
                retainedRetry == null ? null : Timestamp.from(retainedRetry.since()))
            .addValue(
                "retainedRetryAttempts", retainedRetry == null ? null : retainedRetry.attempts())
            .addValue("ttlMillis", ttl.toMillis()));
    jdbc.update(
        """
        DELETE FROM watchbox.watchbox_detection_heartbeat
         WHERE (
                stopped_at IS NOT NULL
                AND stopped_at < now() - (:retentionMillis * INTERVAL '1 millisecond')
               )
            OR (
                stopped_at IS NULL
                AND heartbeat_expires_at < now() - (:retentionMillis * INTERVAL '1 millisecond')
               )
        """,
        Map.of("retentionMillis", DEAD_ROW_RETENTION.toMillis()));
  }

  @Override
  public void markStopped(String instanceId) {
    jdbc.update(
        """
        UPDATE watchbox.watchbox_detection_heartbeat
           SET stopped_at = now(),
               heartbeat_expires_at = now(),
               updated_at = now()
         WHERE instance_id = :instanceId
           AND stopped_at IS NULL
        """,
        Map.of("instanceId", instanceId));
  }

  @Override
  public DetectionLiveness detectionLiveness(Duration stalledThreshold) {
    // JPA won't work here: one snapshot row aggregates the fleet with a count FILTER — JPQL has
    // no FILTER clause, and splitting into per-fact queries would read two different instants.
    // assigned_partitions > 0 is part of alive: an idle poll refreshes the heartbeat without
    // proving assignment, and a consumer with zero assignment detects nothing while polling
    // happily forever. Stall facts count only from the same fresh rows - an expired or stopped
    // row already carries the stronger not-running signal - and the threshold arrives from the
    // caller so read-side policy stays out of the SQL.
    var row =
        jdbc.queryForMap(
            """
            SELECT
              count(*) > 0 AS alive,
              count(*) FILTER (
                WHERE retained_retry_since IS NOT NULL
                  AND retained_retry_since <= now() - (:stalledThresholdMillis * INTERVAL '1 millisecond')
              ) > 0 AS partially_stalled
              FROM watchbox.watchbox_detection_heartbeat
             WHERE stopped_at IS NULL
               AND heartbeat_expires_at > now()
               AND assigned_partitions > 0
            """,
            Map.of("stalledThresholdMillis", stalledThreshold.toMillis()));
    if (!Boolean.TRUE.equals(row.get("alive"))) {
      return DetectionLiveness.NOT_RUNNING;
    }
    return Boolean.TRUE.equals(row.get("partially_stalled"))
        ? DetectionLiveness.PARTIALLY_STALLED
        : DetectionLiveness.RUNNING;
  }
}
