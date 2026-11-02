// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.ProcessorStartupLeader;
import com.opencop.processor.technical.spring.config.watchbox.WatchboxDetectionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WatchboxDetectionGcTest {

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxDetectionRepository repository;
  private MutableClock clock;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    repository = new PostgresWatchboxDetectionRepository(jdbc);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void reset() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    clock = new MutableClock(Instant.parse("2026-07-16T12:00:00Z"));
  }

  @Test
  void tickDeletesOnlyExpiredDetectionsOldestFirstWithinConfiguredBoundAndEventuallyDrains() {
    for (int index = 0; index < 6; index++) {
      insert("expired-" + index, Instant.parse("2026-07-16T10:00:00Z").plusSeconds(index));
    }
    insert("at-cutoff", Instant.parse("2026-07-16T11:00:00Z"));
    var properties = properties(true, 2, 2);
    var registry = new SimpleMeterRegistry();
    var scheduler = scheduler(properties, leader(true), registry);

    scheduler.tick();

    assertThat(objectIds()).containsExactly("at-cutoff", "expired-4", "expired-5");
    assertThat(registry.counter("opencop.watchbox.detection.gc.deleted_rows").count())
        .isEqualTo(4.0);
    assertThat(registry.counter("opencop.watchbox.detection.gc.bound_exhausted").count())
        .isEqualTo(1.0);

    scheduler.tick();

    assertThat(objectIds()).containsExactly("at-cutoff");
    assertThat(registry.counter("opencop.watchbox.detection.gc.deleted_rows").count())
        .isEqualTo(6.0);
    scheduler.stop();
  }

  @Test
  void disabledOrNonLeaderTickIsNoOp() {
    insert("expired", Instant.parse("2026-07-16T10:00:00Z"));
    var disabled = scheduler(properties(false, 1, 1), leader(true), new SimpleMeterRegistry());
    disabled.tick();
    assertThat(objectIds()).containsExactly("expired");
    disabled.stop();

    var nonLeader = scheduler(properties(true, 1, 1), leader(false), new SimpleMeterRegistry());
    nonLeader.tick();
    assertThat(objectIds()).containsExactly("expired");
    nonLeader.stop();
  }

  private WatchboxRetentionCleanupScheduler scheduler(
      WatchboxDetectionProperties properties,
      ProcessorStartupLeader leader,
      SimpleMeterRegistry registry) {
    var gc = properties.getGc();
    return new WatchboxRetentionCleanupScheduler(
        "detection",
        new WatchboxRetentionCleanupScheduler.Settings(
            gc.isEnabled(),
            gc.getInterval(),
            gc.getInitialDelay(),
            gc.getBatchSize(),
            gc.getMaxBatchesPerTick(),
            properties.getRetention()),
        repository::deleteExpiredBatch,
        "opencop.watchbox.detection.gc",
        leader,
        clock,
        new ProcessorMetrics(registry));
  }

  private static WatchboxDetectionProperties properties(
      boolean enabled, int batchSize, int maxBatchesPerTick) {
    var properties = new WatchboxDetectionProperties();
    properties.setRetention(Duration.ofHours(1));
    properties.getGc().setEnabled(enabled);
    properties.getGc().setBatchSize(batchSize);
    properties.getGc().setMaxBatchesPerTick(maxBatchesPerTick);
    return properties;
  }

  private static ProcessorStartupLeader leader(boolean leader) {
    var startupLeader = mock(ProcessorStartupLeader.class);
    when(startupLeader.isLeader()).thenReturn(leader);
    return startupLeader;
  }

  private static void insert(String objectId, Instant eventTime) {
    repository.write(
        List.of(
            new WatchboxTransition(
                new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, objectId),
                UUID.nameUUIDFromBytes(objectId.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                eventTime,
                WatchboxTransitionKind.ENTER,
                null)));
  }

  private static List<String> objectIds() {
    return jdbc.queryForList(
        """
        SELECT object_id
        FROM watchbox.watchbox_detection
        ORDER BY object_id
        """,
        String.class);
  }

  private static final class MutableClock extends Clock {
    private final Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
