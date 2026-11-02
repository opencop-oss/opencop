// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthMaintenance;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Postgres-backed Connect worker health writer and route-fresh resolver.
 *
 * <p>Most persistent domain data should continue to use JPA repositories. This adapter is an
 * intentional exception for ephemeral Postgres-only control-plane rows where the SQL contract is
 * the behavior.
 *
 * <p>Spring Data JPA native queries could execute this SQL, but an entity/projection wrapper would
 * not make the PostgreSQL-specific heartbeat upserts, expiry interval math, registry clearing, or
 * {@code DISTINCT ON} route-fresh lookup more domain-oriented.
 *
 * <p>In a future K8s deployment, pod readiness can own "is this worker up?" truth. This table may
 * then shrink toward app-level metadata such as {@code connection_count}, {@code started_at}, and
 * lifecycle timestamps instead of acting as the pod-up registry.
 */
public class PostgresConnectWorkerHealthRepository
    implements ConnectInstanceResolver, ConnectWorkerHealthWriter, ConnectWorkerHealthMaintenance {

  private static final Logger log =
      LoggerFactory.getLogger(PostgresConnectWorkerHealthRepository.class);
  private static final Duration ROUTABLE_POLL_INTERVAL = Duration.ofMillis(200);

  private static final String FRESH_WORKERS_SQL =
      """
      SELECT DISTINCT ON (instance_id)
             instance_id,
             started_at,
             connection_count,
             registration_id,
             heartbeat_sequence
        FROM profiles.connect_worker_run
       WHERE stopped_at IS NULL
         AND heartbeat_expires_at > now()
       ORDER BY instance_id, last_heartbeat_at DESC, heartbeat_sequence DESC
      """;

  private static final RowMapper<ConnectInstanceInfo> INSTANCE_INFO_MAPPER =
      PostgresConnectWorkerHealthRepository::mapInstanceInfo;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresConnectWorkerHealthRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void writeHeartbeat(UUID workerRunId, ConnectInstanceInfo info, Duration heartbeatTtl) {
    jdbc.update(
        """
        INSERT INTO profiles.connect_worker_run (
          worker_run_id,
          instance_id,
          registration_id,
          started_at,
          last_heartbeat_at,
          heartbeat_expires_at,
          heartbeat_sequence,
          connection_count,
          stopped_at
        )
        VALUES (
          :workerRunId,
          :instanceId,
          :registrationId,
          :startedAt,
          now(),
          now() + (:heartbeatTtlMillis * INTERVAL '1 millisecond'),
          :heartbeatSequence,
          :connectionCount,
          NULL
        )
        ON CONFLICT (registration_id) DO UPDATE SET
          instance_id = EXCLUDED.instance_id,
          started_at = EXCLUDED.started_at,
          last_heartbeat_at = now(),
          heartbeat_expires_at = now() + (:heartbeatTtlMillis * INTERVAL '1 millisecond'),
          heartbeat_sequence = EXCLUDED.heartbeat_sequence,
          connection_count = EXCLUDED.connection_count,
          stopped_at = NULL
        """,
        new MapSqlParameterSource()
            .addValue("workerRunId", workerRunId)
            .addValue("instanceId", info.instanceId())
            .addValue("registrationId", info.registrationId())
            .addValue("startedAt", Timestamp.from(info.startedAt()))
            .addValue("heartbeatTtlMillis", heartbeatTtl.toMillis())
            .addValue("heartbeatSequence", info.heartbeatSequence())
            .addValue("connectionCount", info.connectionCount()));
  }

  @Override
  public void markStopped(UUID workerRunId) {
    jdbc.update(
        """
        UPDATE profiles.connect_worker_run
           SET stopped_at = now(),
               heartbeat_expires_at = now()
         WHERE worker_run_id = :workerRunId
           AND stopped_at IS NULL
        """,
        Map.of("workerRunId", workerRunId));
  }

  @Override
  public int deleteStoppedOrExpiredOlderThan(Duration retention) {
    return jdbc.update(
        """
        DELETE FROM profiles.connect_worker_run
         WHERE (
                stopped_at IS NOT NULL
                AND stopped_at < now() - (:retentionMillis * INTERVAL '1 millisecond')
               )
            OR (
                stopped_at IS NULL
                AND heartbeat_expires_at < now() - (:retentionMillis * INTERVAL '1 millisecond')
               )
        """,
        Map.of("retentionMillis", retention.toMillis()));
  }

  @Override
  public List<String> getAllInstanceIds() {
    return freshInstanceInfos().stream().map(ConnectInstanceInfo::instanceId).toList();
  }

  @Override
  public List<ConnectInstanceInfo> getRoutableInstances(Duration freshnessWait) {
    return getRoutableInstances(freshnessWait, 1);
  }

  @Override
  public List<ConnectInstanceInfo> getRoutableInstances(Duration freshnessWait, int minimumCount) {
    return waitForRoutableInstances(freshInstanceInfos(), freshnessWait, Math.max(1, minimumCount));
  }

  @Override
  public List<ConnectInstanceInfo> getRoutableInstancesForCurrentRegistry(Duration freshnessWait) {
    var baselineInfos = freshInstanceInfos();
    return waitForRoutableInstances(
        baselineInfos, freshnessWait, Math.max(1, baselineInfos.size()));
  }

  private List<ConnectInstanceInfo> waitForRoutableInstances(
      List<ConnectInstanceInfo> baselineInfos, Duration freshnessWait, int requiredCount) {
    var baseline = byInstanceId(baselineInfos);
    var deadline = Instant.now().plus(freshnessWait);

    while (true) {
      var current = freshInstanceInfos();
      var routable = current.stream().filter(info -> isFreshAfterBaseline(info, baseline)).toList();

      if (routable.size() >= requiredCount || !Instant.now().isBefore(deadline)) {
        return routable;
      }

      if (!sleepUntilNextPoll(deadline)) {
        return routable;
      }
    }
  }

  @Override
  public void clearInstanceRegistry() {
    try {
      // Local/Compose startup repair: expire rows that may represent previous runs before waiting
      // for fresh heartbeats. K8s readiness and pod identity may remove or replace this clear.
      jdbc.update(
          """
          UPDATE profiles.connect_worker_run
             SET heartbeat_expires_at = now()
           WHERE stopped_at IS NULL
             AND heartbeat_expires_at > now()
          """,
          Map.of());
      log.info("Expired active Connect worker health rows before startup readiness wait");
    } catch (DataAccessException e) {
      log.warn("Failed to clear Connect worker health registry: {}", e.getMessage());
      throw e;
    }
  }

  private List<ConnectInstanceInfo> freshInstanceInfos() {
    try {
      return jdbc.query(FRESH_WORKERS_SQL, INSTANCE_INFO_MAPPER);
    } catch (DataAccessException e) {
      log.warn("Failed to read Connect worker health from Postgres: {}", e.getMessage());
      return List.of();
    }
  }

  private static Map<String, ConnectInstanceInfo> byInstanceId(List<ConnectInstanceInfo> infos) {
    var byId = new HashMap<String, ConnectInstanceInfo>();
    for (var info : infos) {
      byId.put(info.instanceId(), info);
    }
    return byId;
  }

  private static boolean isFreshAfterBaseline(
      ConnectInstanceInfo current, Map<String, ConnectInstanceInfo> baseline) {
    if (!current.hasRouteFreshnessFields()) {
      return false;
    }

    var previous = baseline.get(current.instanceId());
    if (previous == null || !previous.hasRouteFreshnessFields()) {
      return true;
    }

    return !current.registrationId().equals(previous.registrationId())
        || current.heartbeatSequence() > previous.heartbeatSequence();
  }

  private static boolean sleepUntilNextPoll(Instant deadline) {
    var remaining = Duration.between(Instant.now(), deadline);
    if (remaining.isNegative() || remaining.isZero()) {
      return false;
    }

    var sleep =
        remaining.compareTo(ROUTABLE_POLL_INTERVAL) < 0 ? remaining : ROUTABLE_POLL_INTERVAL;
    try {
      Thread.sleep(sleep.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static ConnectInstanceInfo mapInstanceInfo(ResultSet result, int rowNum)
      throws SQLException {
    return new ConnectInstanceInfo(
        result.getString("instance_id"),
        result.getObject("started_at", OffsetDateTime.class).toInstant(),
        result.getInt("connection_count"),
        result.getString("registration_id"),
        result.getLong("heartbeat_sequence"));
  }
}
