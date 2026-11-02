// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DETECTIONS_EMITTED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DETECTIONS_SUPPRESSED_DUPLICATE;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DETECTIONS_SUPPRESSED_LOST_FOLD;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.REFRESHED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.STALE_DROPPED_TRANSITION;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.processor.business.model.watchbox.WatchboxObservation;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import com.opencop.processor.business.policy.watchbox.PermitAllWatchboxAdmissionGate;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxDetectionRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxAlertOutboxRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxDetectionRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxSourceStatusRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WatchboxTransitionEngineConcurrencyTest {

  private static final WatchboxStateKey KEY =
      new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "concurrent-object");
  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxTransitionStateRepository repository;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase(Map.of("reWriteBatchedInserts", "true"));
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    repository = new PostgresWatchboxTransitionStateRepository(jdbc);
    jdbc.execute(
        """
        CREATE TABLE watchbox.test_state_transition_audit (
          event_id uuid NOT NULL,
          transition text NOT NULL
        )
        """);
    jdbc.execute(
        """
        CREATE FUNCTION watchbox.capture_test_state_transition()
        RETURNS trigger AS $$
        BEGIN
          IF OLD.last_inside IS DISTINCT FROM NEW.last_inside THEN
            INSERT INTO watchbox.test_state_transition_audit (event_id, transition)
            VALUES (NEW.last_event_id, CASE WHEN NEW.last_inside THEN 'ENTER' ELSE 'LEAVE' END);
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """);
    jdbc.execute(
        """
        CREATE TRIGGER capture_test_state_transition
        AFTER UPDATE ON watchbox.watchbox_transition_state
        FOR EACH ROW EXECUTE FUNCTION watchbox.capture_test_state_transition()
        """);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearRows() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    jdbc.update("TRUNCATE watchbox.test_state_transition_audit");
  }

  @Test
  void concurrentSameKeyProcessingEmitsExactlyCommittedStateTransitions() throws Exception {
    var engine = engine(repository, new PostgresWatchboxDetectionRepository(jdbc));
    engine.process(List.of(observation(eventId(0), Instant.parse("2026-07-16T12:00:00Z"), false)));
    var left = new ArrayList<WatchboxObservation>();
    var right = new ArrayList<WatchboxObservation>();
    for (int round = 1; round <= 50; round++) {
      boolean inside = round % 2 == 1;
      int firstEvent = round * 2 - 1;
      int secondEvent = round * 2;
      left.add(
          observation(
              eventId(firstEvent),
              Instant.parse("2026-07-16T12:00:00Z").plusMillis(firstEvent),
              inside));
      right.add(
          observation(
              eventId(secondEvent),
              Instant.parse("2026-07-16T12:00:00Z").plusMillis(secondEvent),
              inside));
    }
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var roundBarrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                for (var observation : left) {
                  roundBarrier.await();
                  engine.process(List.of(observation));
                  roundBarrier.await();
                }
                return null;
              });
      var second =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                for (var observation : right) {
                  roundBarrier.await();
                  engine.process(List.of(observation));
                  roundBarrier.await();
                }
                return null;
              });
      ready.await();
      start.countDown();
      first.get();
      second.get();
    }

    var stateTransitions =
        jdbc.queryForList(
            """
            SELECT event_id || ':' || transition
            FROM watchbox.test_state_transition_audit
            ORDER BY event_id
            """,
            String.class);
    var persistedDetections =
        jdbc.queryForList(
            """
            SELECT event_id || ':' || transition
            FROM watchbox.watchbox_detection
            ORDER BY event_id
            """,
            String.class);

    assertThat(stateTransitions).hasSize(50);
    assertThat(persistedDetections).containsExactlyElementsOf(stateTransitions);
    System.out.printf(
        "watchbox-concurrency-proof committedTransitions=%d persistedDetections=%d%n",
        stateTransitions.size(), persistedDetections.size());
  }

  @Test
  void serializationRetryRecomputesAgainstFreshStateWithoutReplayingEmission() {
    var retryKey = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "retry-object");
    var concurrentEventTime = Instant.parse("2026-07-16T12:00:00.500Z");
    var racingRepository =
        new RacingInsertRepository(repository, dataSource, retryKey, concurrentEventTime);
    var engine = engine(racingRepository, new PostgresWatchboxDetectionRepository(jdbc));

    var result =
        engine.process(
            List.of(
                observation(retryKey, eventId(1), Instant.parse("2026-07-16T12:00:00Z"), false),
                observation(retryKey, eventId(2), Instant.parse("2026-07-16T12:00:01Z"), true)));

    assertThat(racingRepository.raceInjected).isTrue();
    assertThat(result.count(STALE_DROPPED_TRANSITION)).isOne();
    assertThat(result.count(REFRESHED)).isOne();
    assertThat(result.emissions()).isEmpty();
    assertThat(detectionCount()).isZero();
    assertThat(
            currentRows(repository.lockCurrentAndSelectForUpdate(List.of(retryKey)))
                .get(retryKey)
                .lastInside())
        .isTrue();
  }

  @Test
  void exactInsertConflictRetriesAndRefoldsWithoutRolledBackEmission() {
    var conflictKey = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "exact-conflict-object");
    var winnerEventTime = Instant.parse("2026-07-16T12:00:00.500Z");
    var conflictingRepository =
        new ConflictingInsertRepository(repository, dataSource, conflictKey, winnerEventTime);
    var engine = engine(conflictingRepository, new PostgresWatchboxDetectionRepository(jdbc));

    var result =
        engine.process(
            List.of(
                observation(conflictKey, eventId(1), Instant.parse("2026-07-16T12:00:00Z"), false),
                observation(conflictKey, eventId(2), Instant.parse("2026-07-16T12:00:01Z"), true)));

    assertThat(conflictingRepository.conflictInjected).isTrue();
    assertThat(conflictingRepository.firstInsertResult).isEmpty();
    assertThat(conflictingRepository.insertAttempts).isEqualTo(2);
    assertThat(result.count(STALE_DROPPED_TRANSITION)).isOne();
    assertThat(result.count(REFRESHED)).isOne();
    assertThat(result.emissions()).isEmpty();
    assertThat(result.count(DETECTIONS_EMITTED)).isZero();
    assertThat(result.count(DETECTIONS_SUPPRESSED_DUPLICATE)).isZero();
    assertThat(result.count(DETECTIONS_SUPPRESSED_LOST_FOLD)).isOne();
    assertThat(detectionCount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.test_state_transition_audit", Long.class))
        .isZero();
    assertThat(
            currentRows(repository.lockCurrentAndSelectForUpdate(List.of(conflictKey)))
                .get(conflictKey))
        .satisfies(
            state -> {
              assertThat(state.lastInside()).isTrue();
              assertThat(state.lastEventId()).isEqualTo(eventId(2));
              assertThat(state.lastEffectiveEventTime())
                  .isEqualTo(Instant.parse("2026-07-16T12:00:01Z"));
            });
  }

  private static WatchboxTransitionEngine engine(
      WatchboxTransitionStateRepository stateRepository, WatchboxDetectionRepository sink) {
    return new WatchboxTransitionEngine(
        stateRepository,
        new PostgresWatchboxSourceStatusRepository(jdbc),
        sink,
        new PostgresWatchboxAlertOutboxRepository(jdbc),
        new PermitAllWatchboxAdmissionGate(),
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
        Clock.systemUTC(),
        Duration.ofHours(1),
        ProcessorMetrics.noop());
  }

  private static WatchboxObservation observation(UUID eventId, Instant eventTime, boolean inside) {
    return observation(KEY, eventId, eventTime, inside);
  }

  private static WatchboxObservation observation(
      WatchboxStateKey key, UUID eventId, Instant eventTime, boolean inside) {
    return new WatchboxObservation(
        key, eventId, eventTime, inside, inside, WatchboxDirection.BOTH, null);
  }

  private static UUID eventId(long value) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
  }

  private static long detectionCount() {
    return jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_detection", Long.class);
  }

  private static final class RacingInsertRepository implements WatchboxTransitionStateRepository {
    private final WatchboxTransitionStateRepository delegate;
    private final DataSource dataSource;
    private final WatchboxStateKey key;
    private final Instant concurrentEventTime;
    private final AtomicBoolean firstInsert = new AtomicBoolean(true);
    private boolean raceInjected;

    private RacingInsertRepository(
        WatchboxTransitionStateRepository delegate,
        DataSource dataSource,
        WatchboxStateKey key,
        Instant concurrentEventTime) {
      this.delegate = delegate;
      this.dataSource = dataSource;
      this.key = key;
      this.concurrentEventTime = concurrentEventTime;
    }

    @Override
    public WatchboxStateLockResult lockCurrentAndSelectForUpdate(
        Collection<WatchboxStateKey> keys) {
      return delegate.lockCurrentAndSelectForUpdate(keys);
    }

    @Override
    public void upsertAll(List<WatchboxStateRow> rows) {
      delegate.upsertAll(rows);
    }

    @Override
    public Set<WatchboxStateKey> insertAbsent(List<WatchboxStateRow> rows) {
      if (!firstInsert.compareAndSet(true, false)) {
        return delegate.insertAbsent(rows);
      }
      try (var connection = dataSource.getConnection();
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO watchbox.watchbox_transition_state
                    (watchbox_id, config_epoch, source_id, object_id, last_inside,
                     last_geometry_inside, last_event_id, last_effective_event_time,
                     last_observed_at)
                  VALUES (?, ?, ?, ?, true, true, ?, ?, ?)
                  """)) {
        statement.setObject(1, key.watchboxId());
        statement.setLong(2, key.configEpoch());
        statement.setObject(3, key.sourceId());
        statement.setString(4, key.objectId());
        statement.setObject(5, eventId(50));
        statement.setTimestamp(6, Timestamp.from(concurrentEventTime));
        statement.setTimestamp(7, Timestamp.from(Instant.now()));
        statement.executeUpdate();
        raceInjected = true;
        throw new CannotAcquireLockException(
            "injected serialization failure", new SQLException("retry", "40001"));
      } catch (Exception exception) {
        throw new IllegalStateException("failed to inject concurrent insert", exception);
      }
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int batchSize) {
      return delegate.deleteExpiredBatch(cutoff, batchSize);
    }
  }

  private static final class ConflictingInsertRepository
      implements WatchboxTransitionStateRepository {
    private final WatchboxTransitionStateRepository delegate;
    private final DataSource dataSource;
    private final WatchboxStateKey key;
    private final Instant winnerEventTime;
    private final AtomicBoolean firstInsert = new AtomicBoolean(true);
    private Set<WatchboxStateKey> firstInsertResult;
    private boolean conflictInjected;
    private int insertAttempts;

    private ConflictingInsertRepository(
        WatchboxTransitionStateRepository delegate,
        DataSource dataSource,
        WatchboxStateKey key,
        Instant winnerEventTime) {
      this.delegate = delegate;
      this.dataSource = dataSource;
      this.key = key;
      this.winnerEventTime = winnerEventTime;
    }

    @Override
    public WatchboxStateLockResult lockCurrentAndSelectForUpdate(
        Collection<WatchboxStateKey> keys) {
      return delegate.lockCurrentAndSelectForUpdate(keys);
    }

    @Override
    public void upsertAll(List<WatchboxStateRow> rows) {
      delegate.upsertAll(rows);
    }

    @Override
    public Set<WatchboxStateKey> insertAbsent(List<WatchboxStateRow> rows) {
      insertAttempts++;
      if (!firstInsert.compareAndSet(true, false)) {
        return delegate.insertAbsent(rows);
      }
      insertCommittedWinner();
      conflictInjected = true;
      firstInsertResult = delegate.insertAbsent(rows);
      return firstInsertResult;
    }

    private void insertCommittedWinner() {
      try (var connection = dataSource.getConnection();
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO watchbox.watchbox_transition_state
                    (watchbox_id, config_epoch, source_id, object_id, last_inside,
                     last_geometry_inside, last_event_id, last_effective_event_time,
                     last_observed_at)
                  VALUES (?, ?, ?, ?, true, true, ?, ?, ?)
                  """)) {
        statement.setObject(1, key.watchboxId());
        statement.setLong(2, key.configEpoch());
        statement.setObject(3, key.sourceId());
        statement.setString(4, key.objectId());
        statement.setObject(5, eventId(50));
        statement.setTimestamp(6, Timestamp.from(winnerEventTime));
        statement.setTimestamp(7, Timestamp.from(Instant.now()));
        statement.executeUpdate();
      } catch (SQLException exception) {
        throw new IllegalStateException("failed to inject exact insert conflict", exception);
      }
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int batchSize) {
      return delegate.deleteExpiredBatch(cutoff, batchSize);
    }
  }

  private static Map<WatchboxStateKey, WatchboxStateRow> currentRows(
      WatchboxStateLockResult result) {
    return switch (result) {
      case WatchboxStateLockResult.Current(var rows) -> rows;
      case WatchboxStateLockResult.GenerationChanged _ ->
          throw new AssertionError("expected current Watchbox generation");
    };
  }
}
