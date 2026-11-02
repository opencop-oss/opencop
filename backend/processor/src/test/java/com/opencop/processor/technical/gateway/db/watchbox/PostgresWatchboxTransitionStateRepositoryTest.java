// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresWatchboxTransitionStateRepositoryTest {

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
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearRows() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
  }

  @Test
  void emptyInputReturnsImmutableEmptySet() {
    var insertedKeys = repository.insertAbsent(List.of());

    assertThat(insertedKeys).isEmpty();
    assertThatThrownBy(insertedKeys::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void allNewRewriteEnabledBatchReturnsEveryInsertedKey() {
    var rows = rows(50);
    var expectedKeys = rows.stream().map(WatchboxStateRow::key).toList();

    var insertedKeys = repository.insertAbsent(rows);

    assertThat(insertedKeys).containsExactlyInAnyOrderElementsOf(expectedKeys);
    assertThat(stateCount()).isEqualTo(50);
    assertThatThrownBy(insertedKeys::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void mixedConflictReturnsOnlyNewlyInsertedKeys() {
    var rows = rows(50);
    var existing = rows.getFirst();
    repository.upsertAll(List.of(existing));

    var insertedKeys = repository.insertAbsent(rows);

    assertThat(insertedKeys)
        .doesNotContain(existing.key())
        .containsExactlyInAnyOrderElementsOf(
            rows.subList(1, rows.size()).stream().map(WatchboxStateRow::key).toList());
    assertThat(stateCount()).isEqualTo(50);
  }

  @Test
  void tenThousandRowsAvoidBindLimitAndReturnEveryInsertedKey() {
    var rows = rows(10_000);

    var insertedKeys = repository.insertAbsent(rows);

    assertThat(insertedKeys)
        .containsExactlyInAnyOrderElementsOf(rows.stream().map(WatchboxStateRow::key).toList());
    assertThat(stateCount()).isEqualTo(10_000);
  }

  @Test
  void selectForUpdateBeyondBindLimitReturnsEveryExistingKey() {
    var rows = rows(16_500);
    repository.insertAbsent(rows);
    var requested = new ArrayList<>(rows.stream().map(WatchboxStateRow::key).toList());
    requested.add(new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "absent-object"));

    var selected = currentRows(repository.lockCurrentAndSelectForUpdate(requested));

    assertThat(selected).hasSize(16_500);
    var first = rows.getFirst();
    assertThat(selected.get(first.key())).isEqualTo(first);
    var last = rows.getLast();
    assertThat(selected.get(last.key())).isEqualTo(last);
  }

  @Test
  void generationFenceBlocksEpochBumpThenRejectsTheOldGeneration() throws Exception {
    var key = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "generation-lock-object");
    var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    Future<Integer> pendingBump;
    try (var executor = Executors.newSingleThreadExecutor()) {
      pendingBump =
          transactionTemplate.execute(
              _ -> {
                assertThat(currentRows(repository.lockCurrentAndSelectForUpdate(List.of(key))))
                    .isEmpty();
                var bump =
                    executor.submit(
                        () ->
                            jdbc.update(
                                "UPDATE watchbox.watchbox SET config_epoch = config_epoch + 1 WHERE id = ?",
                                WATCHBOX_ID));
                awaitBlockedOperation(bump, "epoch bump");
                return bump;
              });
      assertThat(pendingBump.get(10, TimeUnit.SECONDS)).isOne();
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT config_epoch FROM watchbox.watchbox WHERE id = ?", Long.class, WATCHBOX_ID))
        .isEqualTo(2L);
    assertThat(repository.lockCurrentAndSelectForUpdate(List.of(key)))
        .isInstanceOf(WatchboxStateLockResult.GenerationChanged.class);
  }

  @Test
  void deleteExpiredBatchSkipsRowsReobservedDuringTheDeleteScan() throws Exception {
    var key = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "reseed-object");
    var expiredAt = Instant.parse("2026-07-16T10:00:00Z");
    var cutoff = Instant.parse("2026-07-16T11:30:00Z");
    var refreshedAt = Instant.parse("2026-07-16T11:59:00Z");
    repository.upsertAll(List.of(row(key, expiredAt)));

    var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    Future<Integer> pendingDelete;
    try (var executor = Executors.newSingleThreadExecutor()) {
      pendingDelete =
          transactionTemplate.execute(
              _ -> {
                assertThat(currentRows(repository.lockCurrentAndSelectForUpdate(List.of(key))))
                    .containsKey(key);
                var delete = executor.submit(() -> repository.deleteExpiredBatch(cutoff, 10));
                awaitBlockedOperation(delete, "delete");
                repository.upsertAll(List.of(row(key, refreshedAt)));
                return delete;
              });
      assertThat(pendingDelete.get(10, TimeUnit.SECONDS)).isZero();
    }

    var survivingObservedAt =
        jdbc.queryForObject(
            """
            SELECT last_observed_at FROM watchbox.watchbox_transition_state
            WHERE watchbox_id = ? AND config_epoch = 1 AND source_id = ? AND object_id = ?
            """,
            Timestamp.class,
            WATCHBOX_ID,
            SOURCE_ID,
            key.objectId());
    assertThat(survivingObservedAt.toInstant()).isEqualTo(refreshedAt);
  }

  private static void awaitBlockedOperation(Future<Integer> operation, String description) {
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      if (operation.isDone()) {
        try {
          throw new IllegalStateException(
              description + " finished without blocking; result=" + operation.get());
        } catch (java.util.concurrent.ExecutionException | InterruptedException early) {
          throw new IllegalStateException(description + " failed without blocking", early);
        }
      }
      var waiting =
          jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      try {
        Thread.sleep(25);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted awaiting the blocked delete", interrupted);
      }
    }
    throw new IllegalStateException(description + " never blocked on the expected row lock");
  }

  private static Map<WatchboxStateKey, WatchboxStateRow> currentRows(
      WatchboxStateLockResult result) {
    return switch (result) {
      case WatchboxStateLockResult.Current(var rows) -> rows;
      case WatchboxStateLockResult.GenerationChanged _ ->
          throw new AssertionError("expected current Watchbox generation");
    };
  }

  private static WatchboxStateRow row(WatchboxStateKey key, Instant observedAt) {
    return new WatchboxStateRow(
        key, true, true, eventId(9_000_001L), Instant.parse("2026-07-16T09:59:00Z"), observedAt);
  }

  private static List<WatchboxStateRow> rows(int count) {
    var eventTime = Instant.parse("2026-07-16T12:00:00Z");
    var observedAt = Instant.parse("2026-07-16T12:01:00Z");
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            index ->
                new WatchboxStateRow(
                    new WatchboxStateKey(
                        WATCHBOX_ID, 1, SOURCE_ID, "repository-object-%05d".formatted(index)),
                    true,
                    true,
                    eventId(index + 1L),
                    eventTime.plusMillis(index),
                    observedAt))
        .toList();
  }

  private static long stateCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class);
  }

  private static UUID eventId(long value) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
  }
}
