// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostgresConnectWorkerHealthRepositoryTest {

  private static PostgresGatewayRepositoryTestSupport database;
  private static NamedParameterJdbcTemplate jdbc;

  private PostgresConnectWorkerHealthRepository repository;

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
    jdbc.getJdbcTemplate().execute("TRUNCATE TABLE profiles.connect_worker_run");
    repository = new PostgresConnectWorkerHealthRepository(jdbc);
  }

  @Test
  void writeHeartbeat_insertsAndUpdatesWorkerHealth() {
    var workerRunId = UUID.randomUUID();
    writeHeartbeat(workerRunId, "connect-a", 3, "registration-a", 1, Duration.ofSeconds(30));
    writeHeartbeat(workerRunId, "connect-a", 5, "registration-a", 2, Duration.ofSeconds(30));

    var row =
        jdbc.queryForMap(
            """
            SELECT heartbeat_sequence,
                   connection_count,
                   heartbeat_expires_at > now() AS fresh,
                   stopped_at
              FROM profiles.connect_worker_run
             WHERE worker_run_id = :workerRunId
            """,
            Map.of("workerRunId", workerRunId));

    assertThat(((Number) row.get("heartbeat_sequence")).longValue()).isEqualTo(2);
    assertThat(((Number) row.get("connection_count")).intValue()).isEqualTo(5);
    assertThat(row.get("fresh")).isEqualTo(true);
    assertThat(row.get("stopped_at")).isNull();
    assertThat(repository.getAllInstanceIds()).containsExactly("connect-a");
  }

  @Test
  void routableInstancesIncludeWorkerWhoseHeartbeatAdvancesAfterRouteGate() throws Exception {
    var workerRunId = UUID.randomUUID();
    writeHeartbeat(
        workerRunId, "connect-fresh", 1, "registration-fresh", 1, Duration.ofSeconds(30));

    var updater =
        new Thread(
            () -> {
              sleep(Duration.ofMillis(50));
              writeHeartbeat(
                  workerRunId, "connect-fresh", 1, "registration-fresh", 2, Duration.ofSeconds(30));
            });
    updater.start();

    var routable = repository.getRoutableInstances(Duration.ofSeconds(1));
    updater.join();

    assertThat(routable)
        .extracting(ConnectInstanceInfo::instanceId)
        .containsExactly("connect-fresh");
    assertThat(routable.getFirst().heartbeatSequence()).isEqualTo(2);
  }

  @Test
  void routableInstancesCanWaitForMinimumWorkerCountAfterRouteGate() throws Exception {
    var firstRunId = UUID.randomUUID();
    var secondRunId = UUID.randomUUID();
    var baselineQueryCompleted = new CountDownLatch(1);
    var observedRepository =
        new PostgresConnectWorkerHealthRepository(
            new BaselineObservingNamedParameterJdbcTemplate(jdbc, baselineQueryCompleted));

    var executor = Executors.newSingleThreadExecutor();
    try {
      var routableFuture =
          executor.submit(() -> observedRepository.getRoutableInstances(Duration.ofSeconds(1), 2));

      assertThat(baselineQueryCompleted.await(1, TimeUnit.SECONDS)).isTrue();
      writeHeartbeat(firstRunId, "connect-a", 0, "registration-a", 1, Duration.ofSeconds(30));
      writeHeartbeat(secondRunId, "connect-b", 0, "registration-b", 1, Duration.ofSeconds(30));

      var routable = routableFuture.get(2, TimeUnit.SECONDS);

      assertThat(routable)
          .extracting(ConnectInstanceInfo::instanceId)
          .containsExactly("connect-a", "connect-b");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void routableInstancesForCurrentRegistryWaitsForBaselineWorkersToHeartbeatAfterRouteGate()
      throws Exception {
    var firstRunId = UUID.randomUUID();
    var secondRunId = UUID.randomUUID();
    writeHeartbeat(firstRunId, "connect-a", 0, "registration-a", 1, Duration.ofSeconds(30));
    writeHeartbeat(secondRunId, "connect-b", 0, "registration-b", 1, Duration.ofSeconds(30));

    var updater =
        new Thread(
            () -> {
              sleep(Duration.ofMillis(50));
              writeHeartbeat(
                  firstRunId, "connect-a", 0, "registration-a", 2, Duration.ofSeconds(30));
              sleep(Duration.ofMillis(250));
              writeHeartbeat(
                  secondRunId, "connect-b", 0, "registration-b", 2, Duration.ofSeconds(30));
            });
    updater.start();

    var routable = repository.getRoutableInstancesForCurrentRegistry(Duration.ofSeconds(1));
    updater.join();

    assertThat(routable)
        .extracting(ConnectInstanceInfo::instanceId)
        .containsExactly("connect-a", "connect-b");
    assertThat(routable).extracting(ConnectInstanceInfo::heartbeatSequence).containsOnly(2L);
  }

  @Test
  void missingAndExpiredWorkersAreNotRoutable() {
    assertThat(repository.getAllInstanceIds()).isEmpty();
    assertThat(repository.getRoutableInstances(Duration.ofMillis(10))).isEmpty();

    writeHeartbeat(
        UUID.randomUUID(), "connect-expired", 0, "registration-expired", 1, Duration.ofMillis(-1));

    assertThat(repository.getAllInstanceIds()).isEmpty();
    assertThat(repository.getRoutableInstances(Duration.ofMillis(10))).isEmpty();
  }

  @Test
  void markStoppedRemovesWorkerFromFreshQueries() {
    var workerRunId = UUID.randomUUID();
    writeHeartbeat(
        workerRunId, "connect-stopped", 1, "registration-stopped", 1, Duration.ofSeconds(30));

    repository.markStopped(workerRunId);

    assertThat(repository.getAllInstanceIds()).isEmpty();
    assertThat(repository.getRoutableInstances(Duration.ofMillis(10))).isEmpty();
  }

  @Test
  void replacedWorkerRunForSameInstanceIsRouteFreshAndPreservesHistory() throws Exception {
    var oldRunId = UUID.randomUUID();
    var newRunId = UUID.randomUUID();
    writeHeartbeat(oldRunId, "connect-replaced", 2, "registration-old", 1, Duration.ofSeconds(30));

    var updater =
        new Thread(
            () -> {
              sleep(Duration.ofMillis(50));
              writeHeartbeat(
                  newRunId, "connect-replaced", 1, "registration-new", 1, Duration.ofSeconds(30));
            });
    updater.start();

    var routable = repository.getRoutableInstances(Duration.ofSeconds(1));
    updater.join();

    assertThat(routable).hasSize(1);
    assertThat(routable.getFirst().instanceId()).isEqualTo("connect-replaced");
    assertThat(routable.getFirst().registrationId()).isEqualTo("registration-new");
    assertThat(countRunsForInstance("connect-replaced")).isEqualTo(2);
  }

  @Test
  void leastLoadedRoutableAssignmentIgnoresNonAdvancingLowerLoadWorker() throws Exception {
    writeHeartbeat(
        UUID.randomUUID(),
        "connect-stale-low",
        0,
        "registration-stale-low",
        1,
        Duration.ofSeconds(30));
    var freshRunId = UUID.randomUUID();
    writeHeartbeat(
        freshRunId,
        "connect-fresh-higher",
        4,
        "registration-fresh-higher",
        1,
        Duration.ofSeconds(30));

    var updater =
        new Thread(
            () -> {
              sleep(Duration.ofMillis(50));
              writeHeartbeat(
                  freshRunId,
                  "connect-fresh-higher",
                  4,
                  "registration-fresh-higher",
                  2,
                  Duration.ofSeconds(30));
            });
    updater.start();

    var assigned = repository.getNextRoutableInstanceForAssignment(Duration.ofSeconds(1));
    updater.join();

    assertThat(assigned).isEqualTo("connect-fresh-higher");
  }

  @Test
  void clearInstanceRegistryExpiresActiveRowsUntilNextHeartbeat() {
    var workerRunId = UUID.randomUUID();
    writeHeartbeat(
        workerRunId, "connect-clear", 1, "registration-clear", 1, Duration.ofSeconds(30));

    repository.clearInstanceRegistry();

    assertThat(repository.getAllInstanceIds()).isEmpty();

    writeHeartbeat(
        workerRunId, "connect-clear", 1, "registration-clear", 2, Duration.ofSeconds(30));

    assertThat(repository.getAllInstanceIds()).containsExactly("connect-clear");
  }

  @Test
  void deleteStoppedOrExpiredOlderThan_deletesOnlyRunsBeyondRetention() {
    var stoppedOldRunId = UUID.randomUUID();
    writeHeartbeat(
        stoppedOldRunId,
        "connect-stopped-old",
        1,
        "registration-stopped-old",
        1,
        Duration.ofSeconds(30));
    repository.markStopped(stoppedOldRunId);
    jdbc.update(
        """
        UPDATE profiles.connect_worker_run
           SET stopped_at = now() - INTERVAL '2 hours'
         WHERE worker_run_id = :workerRunId
        """,
        Map.of("workerRunId", stoppedOldRunId));

    var stoppedRecentRunId = UUID.randomUUID();
    writeHeartbeat(
        stoppedRecentRunId,
        "connect-stopped-recent",
        1,
        "registration-stopped-recent",
        1,
        Duration.ofSeconds(30));
    repository.markStopped(stoppedRecentRunId);

    writeHeartbeat(
        UUID.randomUUID(),
        "connect-expired-old",
        1,
        "registration-expired-old",
        1,
        Duration.ofHours(-2));
    writeHeartbeat(
        UUID.randomUUID(),
        "connect-expired-recent",
        1,
        "registration-expired-recent",
        1,
        Duration.ofSeconds(-1));
    writeHeartbeat(
        UUID.randomUUID(), "connect-fresh", 1, "registration-fresh", 1, Duration.ofSeconds(30));

    var deleted = repository.deleteStoppedOrExpiredOlderThan(Duration.ofHours(1));

    assertThat(deleted).isEqualTo(2);
    assertThat(countAllRuns()).isEqualTo(3);
    assertThat(countRunsForInstance("connect-stopped-recent")).isEqualTo(1);
    assertThat(countRunsForInstance("connect-expired-recent")).isEqualTo(1);
    assertThat(countRunsForInstance("connect-fresh")).isEqualTo(1);
  }

  private void writeHeartbeat(
      UUID workerRunId,
      String instanceId,
      int connectionCount,
      String registrationId,
      long heartbeatSequence,
      Duration heartbeatTtl) {
    repository.writeHeartbeat(
        workerRunId,
        new ConnectInstanceInfo(
            instanceId, Instant.now(), connectionCount, registrationId, heartbeatSequence),
        heartbeatTtl);
  }

  private int countRunsForInstance(String instanceId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM profiles.connect_worker_run WHERE instance_id = :instanceId",
        Map.of("instanceId", instanceId),
        Integer.class);
  }

  private int countAllRuns() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM profiles.connect_worker_run", Map.of(), Integer.class);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static final class BaselineObservingNamedParameterJdbcTemplate
      extends NamedParameterJdbcTemplate {
    private final CountDownLatch firstFreshWorkerQueryCompleted;
    private final AtomicBoolean observedFreshWorkerQuery = new AtomicBoolean();

    private BaselineObservingNamedParameterJdbcTemplate(
        NamedParameterJdbcTemplate delegate, CountDownLatch firstFreshWorkerQueryCompleted) {
      super(delegate.getJdbcOperations());
      this.firstFreshWorkerQueryCompleted = firstFreshWorkerQueryCompleted;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
      var result = super.query(sql, rowMapper);
      if (isFreshWorkerQuery(sql) && observedFreshWorkerQuery.compareAndSet(false, true)) {
        firstFreshWorkerQueryCompleted.countDown();
      }
      return result;
    }

    private static boolean isFreshWorkerQuery(String sql) {
      return sql.contains("FROM profiles.connect_worker_run")
          && sql.contains("ORDER BY instance_id, last_heartbeat_at DESC");
    }
  }
}
