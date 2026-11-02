// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.ProcessorStartupLeader;
import com.opencop.processor.technical.spring.config.watchbox.WatchboxSourceStatusProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WatchboxSourceStatusGcTest {

  private static final UUID SECOND_WATCHBOX_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000b22");
  private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxSourceStatusRepository repository;
  private Clock clock;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox
          (id, name, direction, polygon_json, filter_json)
        VALUES (?, ?, 'BOTH', ?::jsonb, '{"predicates":[]}'::jsonb)
        """,
        SECOND_WATCHBOX_ID,
        "wb-test-box-2",
        "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
    repository = new PostgresWatchboxSourceStatusRepository(jdbc);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void reset() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    forceConfigEpoch(SECOND_WATCHBOX_ID, 1);
    clock = Clock.fixed(NOW, ZoneOffset.UTC);
  }

  @Test
  void tickDeletesOnlyEpochStrandedAndAgedRowsAcrossWatchboxes() {
    // Watchbox A advances to epoch 3, stranding generations 1 and 2; watchbox B advances to
    // epoch 2, stranding generation 1. Retention is 1h, so the cutoff is 11:00.
    bumpConfigEpoch(WATCHBOX_ID, 3);
    bumpConfigEpoch(SECOND_WATCHBOX_ID, 2);
    insertStatusRow(WATCHBOX_ID, 1, Instant.parse("2026-07-16T09:00:00Z")); // stranded + aged
    insertStatusRow(WATCHBOX_ID, 2, Instant.parse("2026-07-16T11:30:00Z")); // stranded, fresh
    insertStatusRow(WATCHBOX_ID, 3, Instant.parse("2026-07-16T05:00:00Z")); // current, ancient
    insertStatusRow(SECOND_WATCHBOX_ID, 1, Instant.parse("2026-07-16T09:30:00Z")); // stranded+aged
    insertStatusRow(SECOND_WATCHBOX_ID, 2, Instant.parse("2026-07-16T04:00:00Z")); // current
    var registry = new SimpleMeterRegistry();
    var scheduler = scheduler(properties(true, 5_000, 20), leader(true), registry);

    scheduler.tick();

    // Only the stranded-and-aged generations die; current-epoch rows are immortal regardless of
    // age, and a stranded row still inside the forensic window survives until it ages out.
    assertThat(statusRows())
        .containsExactly(WATCHBOX_ID + ":2", WATCHBOX_ID + ":3", SECOND_WATCHBOX_ID + ":2");
    assertThat(registry.counter("opencop.watchbox.source_status.gc.deleted_rows").count())
        .isEqualTo(2.0);
    assertThat(registry.counter("opencop.watchbox.source_status.gc.bound_exhausted").count())
        .isZero();
    scheduler.stop();
  }

  @Test
  void tickHonorsBatchBoundOldestFirstAndEventuallyDrains() {
    bumpConfigEpoch(WATCHBOX_ID, 3);
    bumpConfigEpoch(SECOND_WATCHBOX_ID, 2);
    insertStatusRow(WATCHBOX_ID, 1, Instant.parse("2026-07-16T09:00:00Z"));
    insertStatusRow(WATCHBOX_ID, 2, Instant.parse("2026-07-16T09:10:00Z"));
    insertStatusRow(SECOND_WATCHBOX_ID, 1, Instant.parse("2026-07-16T09:20:00Z"));
    var registry = new SimpleMeterRegistry();
    var scheduler = scheduler(properties(true, 2, 1), leader(true), registry);

    scheduler.tick();

    assertThat(statusRows()).containsExactly(SECOND_WATCHBOX_ID + ":1");
    assertThat(registry.counter("opencop.watchbox.source_status.gc.deleted_rows").count())
        .isEqualTo(2.0);
    assertThat(registry.counter("opencop.watchbox.source_status.gc.bound_exhausted").count())
        .isEqualTo(1.0);

    scheduler.tick();

    assertThat(statusRows()).isEmpty();
    assertThat(registry.counter("opencop.watchbox.source_status.gc.deleted_rows").count())
        .isEqualTo(3.0);
    scheduler.stop();
  }

  @Test
  void disabledOrNonLeaderTickIsNoOp() {
    bumpConfigEpoch(WATCHBOX_ID, 2);
    insertStatusRow(WATCHBOX_ID, 1, Instant.parse("2026-07-16T09:00:00Z"));
    var disabled = scheduler(properties(false, 1, 1), leader(true), new SimpleMeterRegistry());
    disabled.tick();
    assertThat(statusRows()).containsExactly(WATCHBOX_ID + ":1");
    disabled.stop();

    var nonLeader = scheduler(properties(true, 1, 1), leader(false), new SimpleMeterRegistry());
    nonLeader.tick();
    assertThat(statusRows()).containsExactly(WATCHBOX_ID + ":1");
    nonLeader.stop();
  }

  private WatchboxRetentionCleanupScheduler scheduler(
      WatchboxSourceStatusProperties properties,
      ProcessorStartupLeader leader,
      SimpleMeterRegistry registry) {
    var gc = properties.getGc();
    return new WatchboxRetentionCleanupScheduler(
        "source-status",
        new WatchboxRetentionCleanupScheduler.Settings(
            gc.isEnabled(),
            gc.getInterval(),
            gc.getInitialDelay(),
            gc.getBatchSize(),
            gc.getMaxBatchesPerTick(),
            gc.getRetention()),
        repository::deleteSupersededBatch,
        "opencop.watchbox.source_status.gc",
        leader,
        clock,
        new ProcessorMetrics(registry));
  }

  private static WatchboxSourceStatusProperties properties(
      boolean enabled, int batchSize, int maxBatchesPerTick) {
    var properties = new WatchboxSourceStatusProperties();
    properties.getGc().setEnabled(enabled);
    properties.getGc().setBatchSize(batchSize);
    properties.getGc().setMaxBatchesPerTick(maxBatchesPerTick);
    properties.getGc().setRetention(Duration.ofHours(1));
    return properties;
  }

  private static ProcessorStartupLeader leader(boolean leader) {
    var startupLeader = mock(ProcessorStartupLeader.class);
    when(startupLeader.isLeader()).thenReturn(leader);
    return startupLeader;
  }

  private static void insertStatusRow(UUID watchboxId, long configEpoch, Instant updatedAt) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_source_status
          (watchbox_id, config_epoch, source_id, status, reason_code, since_at, updated_at)
        VALUES (?, ?, ?, 'DEGRADED', 'WATCHBOX_OBJECT_ID_CHURN_DETECTED', ?, ?)
        """,
        watchboxId,
        configEpoch,
        SOURCE_ID,
        Timestamp.from(updatedAt),
        Timestamp.from(updatedAt));
  }

  /** Config epochs are trigger-guarded non-decreasing, so tests advance them with plain updates. */
  private static void bumpConfigEpoch(UUID watchboxId, long configEpoch) {
    jdbc.update(
        "UPDATE watchbox.watchbox SET config_epoch = ? WHERE id = ?", configEpoch, watchboxId);
  }

  /** Rewinds a watchbox to epoch 1 between tests, stepping around the non-decreasing trigger. */
  private static void forceConfigEpoch(UUID watchboxId, long configEpoch) {
    jdbc.execute(
        "ALTER TABLE watchbox.watchbox DISABLE TRIGGER watchbox_config_epoch_non_decreasing");
    try {
      jdbc.update(
          "UPDATE watchbox.watchbox SET config_epoch = ? WHERE id = ?", configEpoch, watchboxId);
    } finally {
      jdbc.execute(
          "ALTER TABLE watchbox.watchbox ENABLE TRIGGER watchbox_config_epoch_non_decreasing");
    }
  }

  private static List<String> statusRows() {
    return jdbc.queryForList(
        """
        SELECT watchbox_id::text || ':' || config_epoch
        FROM watchbox.watchbox_source_status
        ORDER BY watchbox_id, config_epoch
        """,
        String.class);
  }
}
