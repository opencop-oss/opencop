// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxAlertOutboxRow;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertOutboxRepository;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains against real PostgreSQL: the claim/publish/delete transaction semantics under test are
 * exactly the semantics an in-memory fake would have to reimplement.
 */
class WatchboxAlertOutboxDrainerTest {

  // Fixed minutes after the shared transition() event times: the base fixtures sit well inside
  // the delivery window (fresh), and expired fixtures are authored explicitly older than it.
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-14T12:05:00Z"), ZoneOffset.UTC);
  private static final Duration DELIVERY_WINDOW = Duration.ofMinutes(10);

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxAlertOutboxRepository repository;
  private static TransactionTemplate transactionTemplate;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    repository = new PostgresWatchboxAlertOutboxRepository(jdbc);
    transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearRows() {
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
  }

  @Test
  void drainPublishesPendingRowsThenDeletesThem() {
    repository.insertAll(
        List.of(
                transition(1, WatchboxTransitionKind.ENTER),
                transition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var published = new ArrayList<WatchboxAlertEvent>();
    var drainer = drainer(published::add);

    assertThat(drainer.drainAll()).isEqualTo(2);

    assertThat(published)
        .extracting(WatchboxAlertEvent::eventId, WatchboxAlertEvent::kind)
        .containsExactly(
            tuple(eventId(1), WatchboxTransitionKind.ENTER),
            tuple(eventId(2), WatchboxTransitionKind.LEAVE));
    assertThat(outboxCount()).isZero();
  }

  @Test
  void restartAfterCrashBeforeDeliveryEventuallyPublishesTheSameEventIdentity() {
    // The rows exist but no drainer ran: exactly the state a crash between the detection
    // transaction's commit and delivery leaves behind.
    repository.insertAll(
        List.of(transition(7, WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());

    // "Restart": a brand-new drainer with no memory of the previous process.
    var published = new ArrayList<WatchboxAlertEvent>();
    assertThat(drainer(published::add).drainAll()).isOne();

    assertThat(published).extracting(WatchboxAlertEvent::eventId).containsExactly(eventId(7));
    assertThat(outboxCount()).isZero();
  }

  @Test
  void publishFailureRollsBackTheClaimAndTheRetryRedeliversWithTheSameIdentity() {
    repository.insertAll(
        List.of(
                transition(1, WatchboxTransitionKind.ENTER),
                transition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var published = new ArrayList<WatchboxAlertEvent>();
    var failing =
        drainer(
            event -> {
              published.add(event);
              if (event.eventId().equals(eventId(2))) {
                throw new IllegalStateException("redis down");
              }
            });

    assertThatThrownBy(failing::drainAll)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("redis down");

    // The whole claim rolled back: both rows remain, including the one whose publish call
    // succeeded before the failure.
    assertThat(outboxCount()).isEqualTo(2);

    // The retry republishes both with unchanged event identity — the honest at-least-once
    // claim: eventId(1) reaches the wire twice; subscribers deduplicate by it.
    assertThat(drainer(published::add).drainAll()).isEqualTo(2);
    assertThat(published)
        .extracting(WatchboxAlertEvent::eventId)
        .containsExactly(eventId(1), eventId(2), eventId(1), eventId(2));
    assertThat(outboxCount()).isZero();
  }

  @Test
  void staleEpochRowsAreRetiredBeforeCurrentRowsPublish() {
    repository.insertAll(
        List.of(transition(1, 1, WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
    repository.insertAll(
        List.of(transition(2, 2, WatchboxTransitionKind.LEAVE)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var published = new ArrayList<WatchboxAlertEvent>();

    assertThat(drainer(published::add).drainAll()).isOne();

    assertThat(published).extracting(WatchboxAlertEvent::eventId).containsExactly(eventId(2));
    assertThat(outboxCount()).isZero();
  }

  @Test
  void currentPublishFailureRollsBackStaleRetirementAndCurrentDelivery() {
    repository.insertAll(
        List.of(transition(1, 1, WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
    repository.insertAll(
        List.of(transition(2, 2, WatchboxTransitionKind.LEAVE)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var failedAttempt = new ArrayList<WatchboxAlertEvent>();

    assertThatThrownBy(
            () ->
                drainer(
                        event -> {
                          failedAttempt.add(event);
                          throw new IllegalStateException("redis down");
                        })
                    .drainAll())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("redis down");

    // The CTE's stale retirement and the current row's publish/delete share the one pass
    // transaction, so failure leaves both rows for a retry.
    assertThat(failedAttempt).extracting(WatchboxAlertEvent::eventId).containsExactly(eventId(2));
    assertThat(outboxCount()).isEqualTo(2);

    var retried = new ArrayList<WatchboxAlertEvent>();
    assertThat(drainer(retried::add).drainAll()).isOne();
    assertThat(retried).extracting(WatchboxAlertEvent::eventId).containsExactly(eventId(2));
    assertThat(outboxCount()).isZero();
  }

  @Test
  void firstCommittedPassIsCountedEvenWhenTheSecondPassFails() {
    repository.insertAll(
        List.of(
                transition(1, WatchboxTransitionKind.ENTER),
                transition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var registry = new SimpleMeterRegistry();

    assertThatThrownBy(
            () ->
                drainer(
                        event -> {
                          if (event.eventId().equals(eventId(2))) {
                            throw new IllegalStateException("redis down");
                          }
                        },
                        new ProcessorMetrics(registry),
                        1)
                    .drainAll())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("redis down");

    // The first one-row pass committed before the second pass failed, so both its row and its
    // metric remain durable evidence of the work that completed.
    assertThat(outboxCount()).isOne();
    assertThat(registry.counter(WatchboxAlertOutboxDrainer.PUBLISHED_METRIC).count())
        .isEqualTo(1.0);
    assertThat(registry.counter(WatchboxAlertOutboxDrainer.EXPIRED_METRIC).count()).isZero();
  }

  @Test
  void interruptionAfterCommitStopsBeforeTheNextClaimTransaction() {
    repository.insertAll(
        List.of(
                transition(1, WatchboxTransitionKind.ENTER),
                transition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var registry = new SimpleMeterRegistry();
    var claimCalls = new AtomicInteger();
    var interruptingRepository =
        new WatchboxAlertOutboxRepository() {
          @Override
          public void insertAll(List<AcceptedDetection> acceptedDetections) {
            repository.insertAll(acceptedDetections);
          }

          @Override
          public List<WatchboxAlertOutboxRow> claimBatch(int batchSize) {
            claimCalls.incrementAndGet();
            var claimed = repository.claimBatch(batchSize);
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                    Thread.currentThread().interrupt();
                  }
                });
            return claimed;
          }

          @Override
          public void deleteAll(List<Long> outboxIds) {
            repository.deleteAll(outboxIds);
          }

          @Override
          public Optional<Instant> oldestCreatedAt() {
            return repository.oldestCreatedAt();
          }
        };

    try {
      assertThat(
              drainer(interruptingRepository, _ -> {}, new ProcessorMetrics(registry), 1)
                  .drainAll())
          .isOne();

      assertThat(claimCalls).hasValue(1);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(outboxCount()).isOne();
      assertThat(registry.counter(WatchboxAlertOutboxDrainer.PUBLISHED_METRIC).count())
          .isEqualTo(1.0);
    } finally {
      // The interrupt is intentional test evidence; do not leak it to JUnit's worker thread.
      Thread.interrupted();
    }
  }

  @Test
  void expiredRowsAreDeletedWithoutPublishingAndCounted() {
    repository.insertAll(
        List.of(
                transition(1, WatchboxTransitionKind.ENTER),
                expiredTransition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var registry = new SimpleMeterRegistry();
    var published = new ArrayList<WatchboxAlertEvent>();

    assertThat(drainer(published::add, new ProcessorMetrics(registry)).drainAll()).isEqualTo(2);

    // Only the live row reached the wire; the expired row left the table without a publish.
    assertThat(published).extracting(WatchboxAlertEvent::eventId).containsExactly(eventId(1));
    assertThat(outboxCount()).isZero();
    assertThat(registry.counter(WatchboxAlertOutboxDrainer.PUBLISHED_METRIC).count())
        .isEqualTo(1.0);
    assertThat(registry.counter(WatchboxAlertOutboxDrainer.EXPIRED_METRIC).count()).isEqualTo(1.0);
  }

  @Test
  void fullyExpiredBacklogDrainsWhileThePublisherIsStillDown() {
    // The outage endgame: rows aged past the delivery window while Redis was unreachable.
    // Expiry makes no publish call, so the backlog clears even though publishing still fails.
    repository.insertAll(
        List.of(
                expiredTransition(1, WatchboxTransitionKind.ENTER),
                expiredTransition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var stillDown =
        drainer(
            _ -> {
              throw new IllegalStateException("redis down");
            });

    assertThat(stillDown.drainAll()).isEqualTo(2);

    assertThat(outboxCount()).isZero();
  }

  @Test
  void publishFailureRollsBackExpiredDeletionsInTheSameClaim() {
    repository.insertAll(
        List.of(
                transition(1, WatchboxTransitionKind.ENTER),
                expiredTransition(2, WatchboxTransitionKind.LEAVE))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var failing =
        drainer(
            _ -> {
              throw new IllegalStateException("redis down");
            });

    assertThatThrownBy(failing::drainAll).isInstanceOf(IllegalStateException.class);

    // One claim, one transaction: the fresh row's publish failure keeps the expired row too.
    assertThat(outboxCount()).isEqualTo(2);
  }

  @Test
  void backlogLargerThanOneClaimBatchDrainsCompletely() {
    // 130 distinct identities: one full 128-row claim plus a short second claim.
    repository.insertAll(
        LongStream.rangeClosed(1, 130)
            .mapToObj(value -> transition(value, WatchboxTransitionKind.ENTER))
            .toList()
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var published = new ArrayList<WatchboxAlertEvent>();

    assertThat(drainer(published::add).drainAll()).isEqualTo(130);

    assertThat(published).hasSize(130);
    assertThat(outboxCount()).isZero();
  }

  @Test
  void threadDeathPoisonsTheOldestAgeGaugeToItsSentinel() throws Exception {
    // The one path that can kill the drain thread: an Error escaping the per-pass safety net.
    // The sentinel is a load-bearing operator contract (dashboards key on a negative age), so the
    // log-poison-rethrow choreography must not silently regress into a swallowed Error.
    var registry = new SimpleMeterRegistry();
    var dying =
        new WatchboxAlertOutboxDrainer(
            new WatchboxAlertOutboxRepository() {
              @Override
              public void insertAll(List<AcceptedDetection> acceptedDetections) {}

              @Override
              public List<WatchboxAlertOutboxRow> claimBatch(int batchSize) {
                throw new AssertionError("simulated drainer bug");
              }

              @Override
              public void deleteAll(List<Long> outboxIds) {}

              @Override
              public Optional<Instant> oldestCreatedAt() {
                return Optional.empty();
              }
            },
            _ -> {},
            transactionTemplate,
            new WatchboxAlertOutboxDrainer.Settings(128, Duration.ofSeconds(5), DELIVERY_WINDOW),
            CLOCK,
            new ProcessorMetrics(registry));
    try {
      dying.start();
      var gauge = registry.find(WatchboxAlertOutboxDrainer.OLDEST_AGE_METRIC).gauge();
      assertThat(gauge).isNotNull();
      var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      while (gauge.value() != WatchboxAlertOutboxDrainer.DRAINER_DEAD_AGE_SENTINEL
          && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertThat(gauge.value())
          .isEqualTo((double) WatchboxAlertOutboxDrainer.DRAINER_DEAD_AGE_SENTINEL);
    } finally {
      dying.stop();
    }
  }

  @Test
  void drain_publishesDetectionIdAndDisplayNameEndToEnd() {
    var enter = transition(1, WatchboxTransitionKind.ENTER);
    var named =
        new WatchboxTransition(
            enter.key(), enter.eventId(), enter.effectiveEventTime(), enter.kind(), "Named");
    var unnamed = transition(2, WatchboxTransitionKind.LEAVE);
    var detections = new PostgresWatchboxDetectionRepository(jdbc);
    var accepted =
        transactionTemplate.execute(
            status -> {
              var rows = detections.write(List.of(named, unnamed));
              repository.insertAll(rows);
              return rows;
            });
    var published = new ArrayList<WatchboxAlertEvent>();

    assertThat(drainer(published::add).drainAll()).isEqualTo(2);

    assertThat(published)
        .extracting(WatchboxAlertEvent::detectionId)
        .containsExactlyElementsOf(accepted.stream().map(AcceptedDetection::id).toList());
    assertThat(published)
        .extracting(WatchboxAlertEvent::objectDisplayName)
        .containsExactly("Named", null);
    assertThat(outboxCount()).isZero();
  }

  private static WatchboxAlertOutboxDrainer drainer(CachePublisher<WatchboxAlertEvent> publisher) {
    return drainer(publisher, ProcessorMetrics.noop());
  }

  private static WatchboxAlertOutboxDrainer drainer(
      CachePublisher<WatchboxAlertEvent> publisher, ProcessorMetrics metrics) {
    return drainer(repository, publisher, metrics, 128);
  }

  private static WatchboxAlertOutboxDrainer drainer(
      CachePublisher<WatchboxAlertEvent> publisher, ProcessorMetrics metrics, int batchSize) {
    return drainer(repository, publisher, metrics, batchSize);
  }

  private static WatchboxAlertOutboxDrainer drainer(
      WatchboxAlertOutboxRepository outboxRepository,
      CachePublisher<WatchboxAlertEvent> publisher,
      ProcessorMetrics metrics,
      int batchSize) {
    return new WatchboxAlertOutboxDrainer(
        outboxRepository,
        publisher,
        transactionTemplate,
        new WatchboxAlertOutboxDrainer.Settings(batchSize, Duration.ofSeconds(5), DELIVERY_WINDOW),
        CLOCK,
        metrics);
  }

  private static long outboxCount() {
    var count =
        jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_alert_outbox", Long.class);
    return count == null ? -1 : count;
  }

  private static WatchboxTransition transition(long value, WatchboxTransitionKind kind) {
    return transition(value, 1, kind);
  }

  private static WatchboxTransition transition(
      long value, long configEpoch, WatchboxTransitionKind kind) {
    return new WatchboxTransition(
        new WatchboxStateKey(WATCHBOX_ID, configEpoch, SOURCE_ID, "object-" + value),
        eventId(value),
        Instant.parse("2026-08-14T12:00:00Z").plusSeconds(value),
        kind,
        null);
  }

  private static WatchboxTransition expiredTransition(long value, WatchboxTransitionKind kind) {
    // 25 minutes before the fixed clock: well past the 10-minute delivery window.
    return new WatchboxTransition(
        new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "expired-object-" + value),
        eventId(value),
        Instant.parse("2026-08-14T11:40:00Z").plusSeconds(value),
        kind,
        null);
  }

  private static UUID eventId(long value) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
  }
}
