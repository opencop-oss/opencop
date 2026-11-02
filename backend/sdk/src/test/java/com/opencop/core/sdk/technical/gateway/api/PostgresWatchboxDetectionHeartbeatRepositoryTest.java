// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat.RetainedRetry;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader.DetectionLiveness;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostgresWatchboxDetectionHeartbeatRepositoryTest {

  private static final Duration STALLED_THRESHOLD = Duration.ofSeconds(60);

  private static PostgresGatewayRepositoryTestSupport database;
  private static NamedParameterJdbcTemplate jdbc;

  private PostgresWatchboxDetectionHeartbeatRepository repository;

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
    jdbc.getJdbcTemplate().execute("TRUNCATE TABLE watchbox.watchbox_detection_heartbeat");
    repository = new PostgresWatchboxDetectionHeartbeatRepository(jdbc);
  }

  @Test
  void writeHeartbeat_upsertsOneRowPerInstanceAndReportsRunning() {
    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.NOT_RUNNING);

    var startedAt = Instant.parse("2026-08-06T12:00:00Z");
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-a", startedAt, null, 0, 1),
        Duration.ofSeconds(60));
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat(
            "processor-a", startedAt, Instant.parse("2026-08-06T12:00:05Z"), 4, 2),
        Duration.ofSeconds(60));

    var row =
        jdbc.queryForMap(
            """
            SELECT heartbeat_sequence,
                   assigned_partitions,
                   last_evaluated_at,
                   heartbeat_expires_at > now() AS fresh,
                   stopped_at,
                   retained_retry_partitions
              FROM watchbox.watchbox_detection_heartbeat
             WHERE instance_id = :instanceId
            """,
            Map.of("instanceId", "processor-a"));

    assertThat(((Number) row.get("heartbeat_sequence")).longValue()).isEqualTo(2);
    assertThat(((Number) row.get("assigned_partitions")).intValue()).isEqualTo(4);
    assertThat(row.get("last_evaluated_at")).isNotNull();
    assertThat(row.get("fresh")).isEqualTo(true);
    assertThat(row.get("stopped_at")).isNull();
    assertThat(row.get("retained_retry_partitions")).isNull();
    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.RUNNING);
  }

  @Test
  void writeHeartbeat_roundTripsRetainedRetryFactsAndClearsThemOnceTheEpisodeEnds() {
    var since = Instant.parse("2026-08-06T12:00:00Z");
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat(
            "processor-a",
            Instant.now(),
            null,
            2,
            1,
            new RetainedRetry(1, RetainedRetry.Reason.GENERATION_CHANGED, since, 17)),
        Duration.ofSeconds(60));

    var row =
        jdbc.queryForMap(
            """
            SELECT retained_retry_partitions,
                   retained_retry_reason,
                   retained_retry_since,
                   retained_retry_attempts
              FROM watchbox.watchbox_detection_heartbeat
             WHERE instance_id = :instanceId
            """,
            Map.of("instanceId", "processor-a"));
    assertThat(((Number) row.get("retained_retry_partitions")).intValue()).isEqualTo(1);
    assertThat(row.get("retained_retry_reason")).isEqualTo("GENERATION_CHANGED");
    assertThat(((java.sql.Timestamp) row.get("retained_retry_since")).toInstant()).isEqualTo(since);
    assertThat(((Number) row.get("retained_retry_attempts")).longValue()).isEqualTo(17);

    // The next flush without an active episode must clear every fact, not leave a stale wedge.
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-a", Instant.now(), null, 2, 2),
        Duration.ofSeconds(60));
    var cleared =
        jdbc.queryForMap(
            """
            SELECT retained_retry_partitions,
                   retained_retry_reason,
                   retained_retry_since,
                   retained_retry_attempts
              FROM watchbox.watchbox_detection_heartbeat
             WHERE instance_id = :instanceId
            """,
            Map.of("instanceId", "processor-a"));
    assertThat(cleared.values()).containsOnlyNulls();
  }

  @Test
  void retainedRetryColumnsRejectPartialRowsAndUnknownReasons() {
    // The all-or-none CHECK keeps a half-written wedge unrepresentable, and the reason CHECK
    // bounds the vocabulary to the enum the read side and operators grep for.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO watchbox.watchbox_detection_heartbeat
                      (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
                       heartbeat_sequence, retained_retry_partitions)
                    VALUES ('partial', now(), now(), now() + INTERVAL '60 seconds', 1, 1)
                    """,
                    Map.of()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO watchbox.watchbox_detection_heartbeat
                      (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
                       heartbeat_sequence, retained_retry_partitions, retained_retry_reason,
                       retained_retry_since, retained_retry_attempts)
                    VALUES ('unknown-reason', now(), now(), now() + INTERVAL '60 seconds', 1,
                            1, 'REASON_FROM_THE_FUTURE', now(), 1)
                    """,
                    Map.of()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void freshHeartbeatWithZeroAssignedPartitionsReadsAsNotRunning() {
    // An idle poll refreshes the heartbeat without proving assignment; a consumer with zero
    // assignment polls happily forever while detecting nothing. Fresh + unassigned must not
    // present as healthy.
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-a", Instant.now(), null, 0, 1),
        Duration.ofSeconds(60));

    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.NOT_RUNNING);
  }

  @Test
  void expiredHeartbeatReadsAsNotRunning() {
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-a", Instant.now(), null, 2, 1),
        Duration.ofMillis(-1));

    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.NOT_RUNNING);
  }

  @Test
  void retainedRetryOlderThanTheThresholdDegradesAliveToPartiallyStalled() {
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat(
            "processor-a",
            Instant.now(),
            null,
            2,
            1,
            new RetainedRetry(
                1,
                RetainedRetry.Reason.INFRASTRUCTURE,
                Instant.now().minus(Duration.ofMinutes(2)),
                25)),
        Duration.ofSeconds(60));

    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.PARTIALLY_STALLED);
  }

  @Test
  void retainedRetryYoungerThanTheThresholdStillReadsAsRunning() {
    // A wedge below the threshold is expected turbulence (generation churn, a database blip);
    // degrading immediately would flap status on every transient episode.
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat(
            "processor-a",
            Instant.now(),
            null,
            2,
            1,
            new RetainedRetry(
                1,
                RetainedRetry.Reason.GENERATION_CHANGED,
                Instant.now().minus(Duration.ofSeconds(10)),
                2)),
        Duration.ofSeconds(60));

    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.RUNNING);
  }

  @Test
  void stalledFactsOnAnExpiredRowDoNotOutliveTheRowItself() {
    // Multi-instance semantics are any-fresh-row: a healthy fresh instance keeps the answer
    // alive, and a wedge only counts while its own row is fresh - an expired row's stronger
    // signal is absence, not a stale stall.
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat(
            "processor-dead",
            Instant.now(),
            null,
            2,
            1,
            new RetainedRetry(
                2,
                RetainedRetry.Reason.INFRASTRUCTURE,
                Instant.now().minus(Duration.ofMinutes(5)),
                50)),
        Duration.ofMillis(-1));
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-live", Instant.now(), null, 2, 1),
        Duration.ofSeconds(60));

    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.RUNNING);
  }

  @Test
  void anyFreshStalledRowDegradesAMultiInstanceDeployment() {
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-healthy", Instant.now(), null, 2, 1),
        Duration.ofSeconds(60));
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat(
            "processor-wedged",
            Instant.now(),
            null,
            2,
            1,
            new RetainedRetry(
                1,
                RetainedRetry.Reason.INFRASTRUCTURE,
                Instant.now().minus(Duration.ofMinutes(2)),
                25)),
        Duration.ofSeconds(60));

    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.PARTIALLY_STALLED);
  }

  @Test
  void writeHeartbeat_reapsRowsDeadLongerThanTheRetention() {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat
          (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
           heartbeat_sequence, stopped_at)
        VALUES
          ('processor-dead-expired', now() - INTERVAL '3 days', now() - INTERVAL '2 days',
           now() - INTERVAL '2 days', 9, NULL),
          ('processor-dead-stopped', now() - INTERVAL '3 days', now() - INTERVAL '2 days',
           now() - INTERVAL '2 days', 9, now() - INTERVAL '2 days'),
          ('processor-recently-dead', now() - INTERVAL '2 hours', now() - INTERVAL '1 hour',
           now() - INTERVAL '1 hour', 9, NULL)
        """,
        Map.of());

    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-live", Instant.now(), null, 2, 1),
        Duration.ofSeconds(60));

    // Long-dead rows are reaped on the write path; a recently dead one is kept for postmortems.
    assertThat(
            jdbc.queryForList(
                "SELECT instance_id FROM watchbox.watchbox_detection_heartbeat ORDER BY 1",
                Map.of(),
                String.class))
        .containsExactly("processor-live", "processor-recently-dead");
  }

  @Test
  void markStopped_expiresTheRowImmediatelyAndARestartedInstanceRevivesIt() {
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-a", Instant.now(), null, 2, 1),
        Duration.ofSeconds(60));
    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.RUNNING);

    repository.markStopped("processor-a");
    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.NOT_RUNNING);

    // Docker instance ids are container-stable, so a restart reuses the id: its first heartbeat
    // must revive the stopped row. Keeping the shutdown race out of this table is the publisher's
    // job (stop marker is its final write), not this layer's.
    repository.writeHeartbeat(
        new WatchboxDetectionHeartbeat("processor-a", Instant.now(), null, 2, 1),
        Duration.ofSeconds(60));
    assertThat(repository.detectionLiveness(STALLED_THRESHOLD))
        .isEqualTo(DetectionLiveness.RUNNING);
  }
}
