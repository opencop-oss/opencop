// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.api.PostgresWatchboxDetectionHeartbeatRepository;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxAlertOutboxDrainer;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxRetentionCleanupScheduler;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Assembles the production watchbox configuration classes and proves the wiring the unit and
 * component tests deliberately fake: a test that declares its own wiring cannot validate wiring.
 * Property values come from the real {@code application.yml} via {@link
 * ConfigDataApplicationContextInitializer}, so a drifted prefix or key silently binding defaults
 * fails here instead of in production; only the state-GC timing is overridden so the
 * {@code @PostConstruct}-started scheduler observably runs within the test.
 */
class WatchboxConfigWiringTest {

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxTransitionStateRepository stateRepository;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    stateRepository = new PostgresWatchboxTransitionStateRepository(jdbc);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @Test
  void productionWiringBindsYmlAndScheduledGcActuallyRuns() {
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    stateRepository.upsertAll(List.of(expiredRow("expired-by-wiring-test")));
    // A pre-existing stale outbox row stands in for a crash before a config reset committed. The
    // @PostConstruct-started drainer must retire it rather than publish it.
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_alert_outbox
          (watchbox_id, config_epoch, source_id, object_id, event_id, transition, event_time)
        VALUES (?, 1, ?, 'wiring-object', ?, 'ENTER', now())
        """,
        WATCHBOX_ID,
        SOURCE_ID,
        UUID.fromString("00000000-0000-0000-0000-00000000ee01"));
    // An epoch-stranded, aged source-status row: the epoch bump below supersedes generation 1,
    // and updated_at sits past the 72h retention, so the source-status GC arm must delete it.
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_source_status
          (watchbox_id, config_epoch, source_id, status, reason_code, since_at, updated_at)
        VALUES (?, 1, ?, 'DEGRADED', 'WATCHBOX_OBJECT_ID_CHURN_DETECTED',
                now() - interval '73 hours', now() - interval '73 hours')
        """,
        WATCHBOX_ID,
        SOURCE_ID);
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
    // A post-reset row is the current crash-remnant: the startup drainer must publish and delete
    // it without any nudge while retiring the stale row above.
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_alert_outbox
          (watchbox_id, config_epoch, source_id, object_id, event_id, transition, event_time)
        VALUES (?, 2, ?, 'wiring-current-object', ?, 'ENTER', now())
        """,
        WATCHBOX_ID,
        SOURCE_ID,
        UUID.fromString("00000000-0000-0000-0000-00000000ee02"));
    var published = new CopyOnWriteArrayList<WatchboxAlertEvent>();

    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(WatchboxStateConfig.class, WatchboxPipelineConfig.class)
        .withBean(JdbcTemplate.class, () -> jdbc)
        .withBean(NamedParameterJdbcTemplate.class, () -> new NamedParameterJdbcTemplate(jdbc))
        .withBean(
            PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
        .withBean(ProcessorMetrics.class, () -> new ProcessorMetrics(new SimpleMeterRegistry()))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withBean(CachePublisher.class, () -> (CachePublisher<WatchboxAlertEvent>) published::add)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues(
            "opencop.watchbox.state.gc.initial-delay=200ms",
            "opencop.watchbox.state.gc.interval=200ms",
            "opencop.watchbox.source-status.gc.initial-delay=200ms",
            "opencop.watchbox.source-status.gc.interval=200ms",
            "opencop.watchbox.alerts.drain.poll-interval=30s")
        .run(
            context -> {
              assertThat(context).hasNotFailed();

              // The real yml bound through the production prefixes, not defaults of a drifted key.
              var stateProperties = context.getBean(WatchboxStateProperties.class);
              assertThat(stateProperties.getRetention()).isEqualTo(Duration.ofHours(1));
              assertThat(stateProperties.getLiveWindow()).isEqualTo(Duration.ofMinutes(10));
              assertThat(stateProperties.getBreaker().getCap()).isEqualTo(10_000);
              assertThat(stateProperties.getGc().getBatchSize()).isEqualTo(5_000);
              var detectionProperties = context.getBean(WatchboxDetectionProperties.class);
              assertThat(detectionProperties.getRetention()).isEqualTo(Duration.ofHours(72));
              var sourceStatusProperties = context.getBean(WatchboxSourceStatusProperties.class);
              assertThat(sourceStatusProperties.getGc().getRetention())
                  .isEqualTo(Duration.ofHours(72));
              assertThat(sourceStatusProperties.getGc().getBatchSize()).isEqualTo(5_000);
              // The test stretches the otherwise-default fallback poll to make the post-commit
              // nudge assertion below meaningful; this also pins alert-prefix binding.
              var alertProperties = context.getBean(WatchboxAlertProperties.class);
              assertThat(alertProperties.getDrain().getBatchSize()).isEqualTo(128);
              assertThat(alertProperties.getDrain().getPollInterval())
                  .isEqualTo(Duration.ofSeconds(30));

              // The production heartbeat writer, not a test double.
              assertThat(context.getBean(WatchboxDetectionHeartbeatWriter.class))
                  .isInstanceOf(PostgresWatchboxDetectionHeartbeatRepository.class);

              // The event-path beans assemble from the same context. Exactly one runtime-config
              // gateway bean exists and it is the caching decorator — the uncached Postgres
              // adapter is deliberately not injectable.
              assertThat(context).hasSingleBean(WatchboxEventHandler.class);
              assertThat(context).hasSingleBean(WatchboxRuntimeConfigGateway.class);
              assertThat(context.getBean(WatchboxRuntimeConfigGateway.class))
                  .isInstanceOf(
                      com.opencop.processor.technical.gateway.db.watchbox.WatchboxRuntimeConfigCache
                          .class);
              assertThat(context.getBeansOfType(WatchboxRetentionCleanupScheduler.class))
                  .hasSize(3);
              assertThat(context).hasSingleBean(WatchboxAlertOutboxDrainer.class);

              // Executed effect: the @PostConstruct-started, leader-gated state-GC scheduler
              // deletes the expired row on its own schedule — no manual tick().
              awaitTrue(
                  () -> stateRowCount() == 0,
                  Duration.ofSeconds(10),
                  "state GC never deleted the expired row; scheduler start or leader gate broken");

              // Executed effect: the source-status GC arm deletes the epoch-stranded aged row
              // through the same leader gate and schedule machinery.
              awaitTrue(
                  () -> sourceStatusRowCount() == 0,
                  Duration.ofSeconds(10),
                  "source-status GC never deleted the stranded row; arm wiring or binding broken");

              // Executed effect: the drainer's startup drain retires the stale crash-remnant and
              // recovers only the current one through production wiring.
              awaitTrue(
                  () -> outboxRowCount() == 0 && published.size() == 1,
                  Duration.ofSeconds(10),
                  "alert outbox drainer never retired/published the seeded rows on startup");
              assertThat(published.getFirst().eventId())
                  .isEqualTo(UUID.fromString("00000000-0000-0000-0000-00000000ee02"));

              // Make a missed nudge visibly slow: the startup drain is finished and the fallback
              // is now thirty seconds away. The first observation seeds an outside state; the
              // second crosses inside, commits an ENTER, and must wake the real idle drainer.
              published.clear();
              assertThat(outboxRowCount()).isZero();
              assertThat(published).isEmpty();
              WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
              var handler = context.getBean(WatchboxEventHandler.class);
              handler.process(
                  List.of(
                      feature(
                          UUID.fromString("00000000-0000-0000-0000-00000000ee03"),
                          "nudge-object",
                          2,
                          2)));
              handler.process(
                  List.of(
                      feature(
                          UUID.fromString("00000000-0000-0000-0000-00000000ee04"),
                          "nudge-object",
                          0.5,
                          0.5)));

              awaitTrue(
                  () -> outboxRowCount() == 0 && published.size() == 1,
                  Duration.ofSeconds(5),
                  "post-commit Watchbox alert nudge did not drain the idle outbox before fallback");
              assertThat(published.getFirst().eventId())
                  .isEqualTo(UUID.fromString("00000000-0000-0000-0000-00000000ee04"));
            });
  }

  private static Feature feature(UUID eventId, String objectId, double longitude, double latitude) {
    var eventTime = Instant.now();
    var properties =
        new OpenCopProperties.Builder()
            .id(eventId)
            .objectId(objectId)
            .source(SOURCE_ID)
            .timestampz(
                new Timestampz(
                    eventTime.toEpochMilli(),
                    eventTime.toEpochMilli(),
                    eventTime.plusSeconds(600).toEpochMilli()))
            .build();
    return OpenCopPropertiesParser.writeToFeature(
        properties, new Point(new LngLatAlt(longitude, latitude)));
  }

  private static long stateRowCount() {
    var count =
        jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class);
    return count == null ? -1 : count;
  }

  private static long outboxRowCount() {
    var count =
        jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_alert_outbox", Long.class);
    return count == null ? -1 : count;
  }

  private static long sourceStatusRowCount() {
    var count =
        jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_source_status", Long.class);
    return count == null ? -1 : count;
  }

  private static WatchboxStateRow expiredRow(String objectId) {
    return new WatchboxStateRow(
        new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, objectId),
        false,
        false,
        UUID.nameUUIDFromBytes(objectId.getBytes(StandardCharsets.UTF_8)),
        Instant.now().minus(Duration.ofHours(3)),
        Instant.now().minus(Duration.ofHours(2)));
  }

  private static void awaitTrue(BooleanSupplier condition, Duration timeout, String failure) {
    var deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted while waiting: " + failure, e);
      }
    }
    throw new AssertionError(failure);
  }
}
