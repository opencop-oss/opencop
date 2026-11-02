// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostgresSourceRuntimeObservationRepositoryTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445001");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655445002");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655445003");
  private static final Instant LAST_MESSAGE_AT = Instant.parse("2026-05-02T14:30:15Z");
  private static final Instant CONNECTOR_STARTED_AT = Instant.parse("2026-05-02T14:29:00Z");

  private static PostgresGatewayRepositoryTestSupport database;
  private static NamedParameterJdbcTemplate jdbc;

  private PostgresSourceRuntimeObservationRepository repository;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = PostgresGatewayRepositoryTestSupport.start();
    jdbc = database.jdbc();
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    if (database != null) {
      database.close();
    }
  }

  @BeforeEach
  void setUp() {
    repository = new PostgresSourceRuntimeObservationRepository(jdbc);
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.source_runtime_observation");
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.socket_source_restart");
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.data_source");
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.data_mapper");
    database.seedMapper(MAPPER_ID);
    database.seedSocketSource(SOURCE_ID, MAPPER_ID);
    database.seedSocketSource(OTHER_SOURCE_ID, MAPPER_ID);
  }

  @Test
  void writeRuntime_insertsFreshObservation() {
    repository.writeRuntime(
        new RuntimeStatus(
            SOURCE_ID,
            ConnectorState.CONNECTED,
            LAST_MESSAGE_AT,
            "connect-a",
            CONNECTOR_STARTED_AT),
        Duration.ofSeconds(30));

    var runtime = repository.findFresh(SOURCE_ID);

    assertThat(runtime).isPresent();
    assertThat(runtime.get().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(runtime.get().lastMessageAt()).isEqualTo(LAST_MESSAGE_AT);
    assertThat(runtime.get().instanceId()).isEqualTo("connect-a");
    assertThat(runtime.get().connectorStartedAt()).isEqualTo(CONNECTOR_STARTED_AT);
  }

  @Test
  void writeRuntime_persistsIncompatibleObservation() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.INCOMPATIBLE, null, "connect-a"),
        Duration.ofSeconds(30));

    var runtime = repository.findFresh(SOURCE_ID);

    assertThat(runtime).isPresent();
    assertThat(runtime.get().state()).isEqualTo(ConnectorState.INCOMPATIBLE);
    assertThat(runtime.get().instanceId()).isEqualTo("connect-a");
  }

  /** Every reason must be admitted by the migrated CHECK, so a new one needs its migration. */
  @ParameterizedTest
  @EnumSource(ConnectorStateReason.class)
  void writeRuntime_persistsAFailedObservationWithItsStateReason(ConnectorStateReason reason) {
    repository.writeRuntime(
        RuntimeStatus.failed(SOURCE_ID, "connect-a", reason), Duration.ofSeconds(30));

    var runtime = repository.findFreshForInstance(SOURCE_ID, "connect-a");

    assertThat(runtime).isPresent();
    assertThat(runtime.get().state()).isEqualTo(ConnectorState.FAILED);
    assertThat(runtime.get().stateReason()).isEqualTo(reason);
  }

  @Test
  void writeRuntime_withoutAStateReasonClearsThePreviousOne() {
    repository.writeRuntime(unavailable("connect-a"), Duration.ofSeconds(30));

    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.FAILED, null, "connect-a"),
        Duration.ofSeconds(30));

    assertThat(repository.findFresh(SOURCE_ID).orElseThrow().stateReason()).isNull();
  }

  /**
   * A previous-release writer upserts without naming the column, so only the database can clear a
   * reason it would otherwise leave beside the state it reports.
   */
  @Test
  void legacyUpsertThatOmitsTheStateReasonClearsItOnLeavingFailed() {
    repository.writeRuntime(unavailable("connect-a"), Duration.ofSeconds(30));

    jdbc.update(
        """
        INSERT INTO profiles.source_runtime_observation (
          source_id, observed_instance_id, state, observed_at, observation_expires_at)
        VALUES (:sourceId, 'connect-a', 'CONNECTING', now(), now() + INTERVAL '30 seconds')
        ON CONFLICT (source_id, observed_instance_id) DO UPDATE SET
          state = EXCLUDED.state,
          observed_at = now(),
          observation_expires_at = now() + INTERVAL '30 seconds'
        """,
        Map.of("sourceId", SOURCE_ID));

    var runtime = repository.findFresh(SOURCE_ID).orElseThrow();
    assertThat(runtime.state()).isEqualTo(ConnectorState.CONNECTING);
    assertThat(runtime.stateReason()).isNull();
  }

  @Test
  void aStateReasonIsDroppedFromAnInsertedNonFailedState() {
    jdbc.update(
        """
        INSERT INTO profiles.source_runtime_observation (
          source_id, observed_instance_id, state, state_reason, observed_at, observation_expires_at)
        VALUES (:sourceId, 'connect-a', 'CONNECTED', 'INTEGRATION_UNAVAILABLE', now(),
                now() + INTERVAL '30 seconds')
        """,
        Map.of("sourceId", SOURCE_ID));

    assertThat(repository.findFresh(SOURCE_ID).orElseThrow().stateReason()).isNull();
  }

  @Test
  void rejectsAStateReasonOutsideTheKnownCodes() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO profiles.source_runtime_observation (
                      source_id, observed_instance_id, state, state_reason, observed_at,
                      observation_expires_at)
                    VALUES (:sourceId, 'connect-a', 'FAILED', 'SOMETHING_ELSE', now(),
                            now() + INTERVAL '30 seconds')
                    """,
                    Map.of("sourceId", SOURCE_ID)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /**
   * A later release can widen the CHECK with a reason this release does not define, and a rollback
   * keeps both the widened constraint and the rows written under it.
   */
  @Test
  void readsAReasonFromALaterReleaseAsNoReason() {
    widenStateReasonCheckTo("'INTEGRATION_UNAVAILABLE', 'FROM_A_LATER_RELEASE'");
    try {
      jdbc.update(
          """
          INSERT INTO profiles.source_runtime_observation (
            source_id, observed_instance_id, state, state_reason, observed_at,
            observation_expires_at)
          VALUES (:sourceId, 'connect-a', 'FAILED', 'FROM_A_LATER_RELEASE', now(),
                  now() + INTERVAL '30 seconds')
          """,
          Map.of("sourceId", SOURCE_ID));

      var runtime = repository.findFreshForInstance(SOURCE_ID, "connect-a").orElseThrow();

      assertThat(runtime.state()).isEqualTo(ConnectorState.FAILED);
      assertThat(runtime.stateReason()).isNull();
    } finally {
      jdbc.getJdbcTemplate().execute("DELETE FROM profiles.source_runtime_observation");
      widenStateReasonCheckTo("'INTEGRATION_UNAVAILABLE'");
    }
  }

  @Test
  void writeState_preservesRuntimeMetadataForSameInstance() {
    repository.writeRuntime(
        new RuntimeStatus(
            SOURCE_ID,
            ConnectorState.CONNECTED,
            LAST_MESSAGE_AT,
            "connect-a",
            CONNECTOR_STARTED_AT),
        Duration.ofSeconds(30));

    repository.writeState(
        SOURCE_ID, ConnectorState.DISCONNECTED, "connect-a", Duration.ofSeconds(30));

    var runtime = repository.findFresh(SOURCE_ID);

    assertThat(runtime).isPresent();
    assertThat(runtime.get().state()).isEqualTo(ConnectorState.DISCONNECTED);
    assertThat(runtime.get().lastMessageAt()).isEqualTo(LAST_MESSAGE_AT);
    assertThat(runtime.get().connectorStartedAt()).isEqualTo(CONNECTOR_STARTED_AT);
  }

  @Test
  void writeRuntime_preservesNewerLastMessageAtForSameInstance() {
    var newer = LAST_MESSAGE_AT.plusSeconds(10);
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, newer, "connect-a"),
        Duration.ofSeconds(30));

    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a"),
        Duration.ofSeconds(30));

    var runtime = repository.findFreshForInstance(SOURCE_ID, "connect-a");

    assertThat(runtime).isPresent();
    assertThat(runtime.get().lastMessageAt()).isEqualTo(newer);
  }

  @Test
  void findFresh_returnsLatestFreshObservationAcrossInstances() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a"),
        Duration.ofSeconds(30));
    sleep(Duration.ofMillis(5));
    repository.writeRuntime(
        new RuntimeStatus(
            SOURCE_ID,
            ConnectorState.CONNECTING,
            LAST_MESSAGE_AT.plusSeconds(10),
            "connect-wrong-owner"),
        Duration.ofSeconds(30));

    var runtime = repository.findFresh(SOURCE_ID);

    assertThat(countRowsForSource(SOURCE_ID)).isEqualTo(2);
    assertThat(runtime).isPresent();
    assertThat(runtime.get().state()).isEqualTo(ConnectorState.CONNECTING);
    assertThat(runtime.get().instanceId()).isEqualTo("connect-wrong-owner");
  }

  @Test
  void findFreshForInstance_returnsAssignedInstanceObservation() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a"),
        Duration.ofSeconds(30));
    repository.writeRuntime(
        new RuntimeStatus(
            SOURCE_ID,
            ConnectorState.CONNECTING,
            LAST_MESSAGE_AT.plusSeconds(10),
            "connect-wrong-owner"),
        Duration.ofSeconds(30));

    var runtime = repository.findFreshForInstance(SOURCE_ID, "connect-a");

    assertThat(runtime).isPresent();
    assertThat(runtime.get().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(runtime.get().lastMessageAt()).isEqualTo(LAST_MESSAGE_AT);
    assertThat(runtime.get().instanceId()).isEqualTo("connect-a");
  }

  @Test
  void findFreshForInstance_returnsEmptyWhenOnlyWrongOwnerObservationExists() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-wrong"),
        Duration.ofSeconds(30));

    assertThat(repository.findFreshForInstance(SOURCE_ID, "connect-assigned")).isEmpty();
  }

  @Test
  void findFreshForInstance_ignoresExpiredAssignedObservation() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a"),
        Duration.ofMillis(-1));

    assertThat(repository.findFreshForInstance(SOURCE_ID, "connect-a")).isEmpty();
  }

  @Test
  void findFreshForInstance_returnsOlderAssignedObservationWhenWrongOwnerIsNewer() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a"),
        Duration.ofSeconds(30));
    sleep(Duration.ofMillis(5));
    repository.writeRuntime(
        new RuntimeStatus(
            SOURCE_ID,
            ConnectorState.CONNECTING,
            LAST_MESSAGE_AT.plusSeconds(10),
            "connect-wrong-owner"),
        Duration.ofSeconds(30));

    var latest = repository.findFresh(SOURCE_ID);
    var assigned = repository.findFreshForInstance(SOURCE_ID, "connect-a");

    assertThat(latest).isPresent();
    assertThat(latest.get().instanceId()).isEqualTo("connect-wrong-owner");
    assertThat(assigned).isPresent();
    assertThat(assigned.get().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(assigned.get().instanceId()).isEqualTo("connect-a");
  }

  @Test
  void findFresh_ignoresExpiredObservations() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-expired"),
        Duration.ofMillis(-1));

    assertThat(repository.findFresh(SOURCE_ID)).isEmpty();
  }

  @Test
  void sourceDeleteCascadesRuntimeObservations() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a"),
        Duration.ofSeconds(30));

    jdbc.update(
        "DELETE FROM profiles.data_source WHERE id = :sourceId", Map.of("sourceId", SOURCE_ID));

    assertThat(countRowsForSource(SOURCE_ID)).isZero();
  }

  @Test
  void deleteExpiredOlderThan_deletesOnlyRowsBeyondRetentionAndReturnsCount() {
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-old"),
        Duration.ofHours(-2));
    repository.writeRuntime(
        new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-recent"),
        Duration.ofSeconds(-1));
    repository.writeRuntime(
        new RuntimeStatus(
            OTHER_SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-fresh"),
        Duration.ofSeconds(30));

    var deleted = repository.deleteExpiredOlderThan(Duration.ofHours(1));

    assertThat(deleted).isEqualTo(1);
    assertThat(countRowsForSource(SOURCE_ID)).isEqualTo(1);
    assertThat(countRowsForSource(OTHER_SOURCE_ID)).isEqualTo(1);
  }

  private int countRowsForSource(UUID sourceId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM profiles.source_runtime_observation WHERE source_id = :sourceId",
        Map.of("sourceId", sourceId),
        Integer.class);
  }

  private static void widenStateReasonCheckTo(String codes) {
    jdbc.getJdbcTemplate()
        .execute(
            """
            ALTER TABLE profiles.source_runtime_observation
              DROP CONSTRAINT source_runtime_observation_state_reason_check,
              ADD CONSTRAINT source_runtime_observation_state_reason_check
                CHECK (state_reason IS NULL OR (state = 'FAILED' AND state_reason IN (%s)))
            """
                .formatted(codes));
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static RuntimeStatus unavailable(String instanceId) {
    return RuntimeStatus.failed(
        SOURCE_ID, instanceId, ConnectorStateReason.INTEGRATION_UNAVAILABLE);
  }
}
