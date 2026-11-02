// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationMaintenance;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Postgres-backed source runtime observation writer and reader.
 *
 * <p>Most persistent domain data should continue to use JPA repositories. This adapter is an
 * intentional exception for ephemeral Postgres-only runtime observations where freshness, expiry,
 * and atomic merge semantics are the main contract.
 *
 * <p>Spring Data JPA native queries could execute this SQL, but an entity/projection wrapper would
 * mostly hide the PostgreSQL-specific upsert, timestamp/TTL interval math, cleanup-by-expiry, and
 * last-message preservation rules without adding useful entity lifecycle behavior.
 */
public class PostgresSourceRuntimeObservationRepository
    implements SourceRuntimeObservationReader,
        SourceRuntimeObservationWriter,
        SourceRuntimeObservationMaintenance {

  private static final RowMapper<RuntimeStatus> RUNTIME_STATUS_MAPPER =
      PostgresSourceRuntimeObservationRepository::mapRuntimeStatus;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresSourceRuntimeObservationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void writeState(
      UUID sourceId, ConnectorState state, String observedInstanceId, Duration observationTtl) {
    writeObservation(sourceId, state, null, observedInstanceId, null, null, null, observationTtl);
  }

  @Override
  public void writeRuntime(RuntimeStatus runtime, Duration observationTtl) {
    writeObservation(
        runtime.id(),
        runtime.state(),
        runtime.lastMessageAt(),
        runtime.instanceId(),
        runtime.connectorStartedAt(),
        runtime.lastMessageToken(),
        runtime.stateReason(),
        observationTtl);
  }

  @Override
  public Optional<RuntimeStatus> findFresh(UUID sourceId) {
    try {
      return Optional.of(
          // Plain SQL over the expiring runtime read model; the class note says why, not JPA.
          jdbc.queryForObject(
              """
              SELECT source_id,
                     observed_instance_id,
                     state,
                     last_message_at,
                     connector_started_at,
                     state_reason
                FROM profiles.source_runtime_observation
               WHERE source_id = :sourceId
                 AND observation_expires_at > now()
               ORDER BY observed_at DESC,
                        observation_expires_at DESC,
                        observed_instance_id
               LIMIT 1
              """,
              Map.of("sourceId", sourceId),
              RUNTIME_STATUS_MAPPER));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<RuntimeStatus> findFreshForInstance(UUID sourceId, String observedInstanceId) {
    try {
      return Optional.of(
          // Plain SQL over the expiring runtime read model; the class note says why, not JPA.
          jdbc.queryForObject(
              """
              SELECT source_id,
                     observed_instance_id,
                     state,
                     last_message_at,
                     connector_started_at,
                     state_reason
                FROM profiles.source_runtime_observation
               WHERE source_id = :sourceId
                 AND observed_instance_id = :observedInstanceId
                 AND observation_expires_at > now()
              """,
              new MapSqlParameterSource()
                  .addValue("sourceId", sourceId)
                  .addValue("observedInstanceId", observedInstanceId),
              RUNTIME_STATUS_MAPPER));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  @Override
  public int deleteExpiredOlderThan(Duration retention) {
    return jdbc.update(
        """
        DELETE FROM profiles.source_runtime_observation
         WHERE observation_expires_at < now() - (:retentionMillis * INTERVAL '1 millisecond')
        """,
        Map.of("retentionMillis", retention.toMillis()));
  }

  private void writeObservation(
      UUID sourceId,
      ConnectorState state,
      Instant lastMessageAt,
      String observedInstanceId,
      Instant connectorStartedAt,
      String lastMessageToken,
      ConnectorStateReason stateReason,
      Duration observationTtl) {
    // One PostgreSQL upsert keeps the merge rules and the TTL atomic; see the class note on why
    // this is not JPA.
    jdbc.update(
        """
        INSERT INTO profiles.source_runtime_observation (
          source_id,
          observed_instance_id,
          state,
          last_message_at,
          connector_started_at,
          last_message_token,
          state_reason,
          observed_at,
          observation_expires_at
        )
        VALUES (
          :sourceId,
          :observedInstanceId,
          :state,
          :lastMessageAt,
          :connectorStartedAt,
          :lastMessageToken,
          :stateReason,
          now(),
          now() + (:observationTtlMillis * INTERVAL '1 millisecond')
        )
        ON CONFLICT (source_id, observed_instance_id) DO UPDATE SET
          state = EXCLUDED.state,
          last_message_at = CASE
              WHEN EXCLUDED.last_message_at IS NULL THEN
                  profiles.source_runtime_observation.last_message_at
              WHEN profiles.source_runtime_observation.last_message_at IS NULL THEN
                  EXCLUDED.last_message_at
              ELSE GREATEST(
                  EXCLUDED.last_message_at,
                  profiles.source_runtime_observation.last_message_at
              )
          END,
          connector_started_at = COALESCE(
              EXCLUDED.connector_started_at,
              profiles.source_runtime_observation.connector_started_at
          ),
          -- Preserved on null for the same reason as connector_started_at: state-only writes
          -- report no message, and erasing the token would break the activity trigger's
          -- new-or-changed test, re-firing on a token it had already accounted for.
          last_message_token = COALESCE(
              EXCLUDED.last_message_token,
              profiles.source_runtime_observation.last_message_token
          ),
          -- Unlike the metadata above, null is a statement here: the writer reports no reason.
          state_reason = EXCLUDED.state_reason,
          observed_at = now(),
          observation_expires_at = now() + (:observationTtlMillis * INTERVAL '1 millisecond')
        """,
        new MapSqlParameterSource()
            .addValue("sourceId", sourceId)
            .addValue("observedInstanceId", observedInstanceId)
            .addValue("state", state.name())
            .addValue("lastMessageAt", lastMessageAt == null ? null : Timestamp.from(lastMessageAt))
            .addValue(
                "connectorStartedAt",
                connectorStartedAt == null ? null : Timestamp.from(connectorStartedAt))
            .addValue("lastMessageToken", lastMessageToken)
            .addValue("stateReason", stateReason == null ? null : stateReason.name())
            .addValue("observationTtlMillis", observationTtl.toMillis()));
  }

  private static RuntimeStatus mapRuntimeStatus(ResultSet result, int rowNum) throws SQLException {
    var lastMessageAt = result.getObject("last_message_at", OffsetDateTime.class);
    var connectorStartedAt = result.getObject("connector_started_at", OffsetDateTime.class);
    return new RuntimeStatus(
        result.getObject("source_id", UUID.class),
        ConnectorState.valueOf(result.getString("state")),
        lastMessageAt == null ? null : lastMessageAt.toInstant(),
        result.getString("observed_instance_id"),
        connectorStartedAt == null ? null : connectorStartedAt.toInstant(),
        null,
        readStateReason(result.getString("state_reason")));
  }

  /**
   * Reads an unrecognised code as no reason: a later release can widen the state_reason CHECK, and
   * Flyway does not narrow it again on rollback, so this release may meet codes it never defined.
   */
  private static @Nullable ConnectorStateReason readStateReason(@Nullable String code) {
    if (code == null) {
      return null;
    }
    for (var reason : ConnectorStateReason.values()) {
      if (reason.name().equals(code)) {
        return reason;
      }
    }
    return null;
  }
}
