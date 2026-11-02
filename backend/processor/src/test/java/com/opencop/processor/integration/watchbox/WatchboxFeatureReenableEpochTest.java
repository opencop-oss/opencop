// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.integration.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.policy.watchbox.PermitAllWatchboxAdmissionGate;
import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import com.opencop.processor.business.usecase.watchbox.WatchboxTransitionEngine;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxAlertOutboxRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxDetectionRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxRuntimeConfigRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxSourceStatusRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxRuntimeConfigCache;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class WatchboxFeatureReenableEpochTest {

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static DataSourceTransactionManager transactionManager;

  private PostgresWatchboxRuntimeConfigRepository configRepository;
  private WatchboxRuntimeConfigCache runtimeConfig;
  private SimpleMeterRegistry registry;
  private WatchboxEventHandler handler;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase(Map.of("reWriteBatchedInserts", "true"));
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void reset() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    var objectMapper = new ObjectMapper();
    registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    configRepository = new PostgresWatchboxRuntimeConfigRepository(jdbc, objectMapper, metrics);
    runtimeConfig = new WatchboxRuntimeConfigCache(configRepository, Duration.ofHours(1), metrics);
    var engine =
        new WatchboxTransitionEngine(
            new PostgresWatchboxTransitionStateRepository(jdbc),
            new PostgresWatchboxSourceStatusRepository(jdbc),
            new PostgresWatchboxDetectionRepository(jdbc),
            new PostgresWatchboxAlertOutboxRepository(jdbc),
            new PermitAllWatchboxAdmissionGate(),
            new TransactionTemplate(transactionManager),
            Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC),
            Duration.ofHours(1),
            metrics);
    handler =
        new WatchboxEventHandler(
            runtimeConfig,
            engine,
            () -> {},
            Duration.ofHours(2),
            Duration.ofMinutes(5),
            objectMapper,
            Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC),
            metrics);
  }

  @Test
  void process_whenCachedLiveEpochSurvivesRapidReenable_reloadsAndSeedsSilently() {
    var beforeDisable = runtimeConfig.load().targetsFor(SOURCE_ID).getFirst();
    assertThat(beforeDisable.configEpoch()).isEqualTo(1);
    handler.process(List.of(feature(1, "2026-08-13T11:00:00Z", 2, 2)));
    assertThat(stateRows()).containsExactly("1:false");

    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "NONE");
    assertThat(configRepository.load().isEmpty()).isTrue();
    reenable(true);

    var afterReenable = configRepository.load().targetsFor(SOURCE_ID).getFirst();
    assertThat(afterReenable.configEpoch()).isEqualTo(2);
    // No Processor load observed NONE, so the production cache still carries epoch 1 here.
    assertThat(runtimeConfig.load().targetsFor(SOURCE_ID).getFirst().configEpoch()).isEqualTo(1);
    handler.process(List.of(feature(2, "2026-08-13T11:02:00Z", 0.5, 0.5)));

    assertThat(stateRows()).containsExactly("1:false", "2:true");
    assertThat(detectionCount()).isZero();
    assertThat(runtimeConfig.load().targetsFor(SOURCE_ID).getFirst().configEpoch()).isEqualTo(2);
    assertThat(registry.counter("opencop.watchbox.pipeline.config.generation_retries").count())
        .isOne();
  }

  @Test
  void process_whenReenabledWithoutEpochBump_emitsCounterfactualCrossing() {
    handler.process(List.of(feature(1, "2026-08-13T11:00:00Z", 2, 2)));
    assertThat(stateRows()).containsExactly("1:false");

    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "NONE");
    reenable(false);
    handler.process(List.of(feature(2, "2026-08-13T11:02:00Z", 0.5, 0.5)));

    assertThat(stateRows()).containsExactly("1:true");
    assertThat(detectionCount()).isOne();
  }

  private static void reenable(boolean bumpEpoch) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbc.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'ALL' WHERE name = 'watchbox'");
              if (bumpEpoch) {
                jdbc.update(
                    "UPDATE watchbox.watchbox SET config_epoch = config_epoch + 1 WHERE id = ?",
                    WATCHBOX_ID);
              }
            });
  }

  private static Feature feature(
      long sequence, String eventTimeText, double longitude, double latitude) {
    var eventTime = Instant.parse(eventTimeText);
    var properties =
        new OpenCopProperties.Builder()
            .id(UUID.fromString("00000000-0000-0000-0000-%012d".formatted(sequence)))
            .objectId("object-1")
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

  private static List<String> stateRows() {
    return jdbc.queryForList(
        """
        SELECT config_epoch || ':' || last_inside
        FROM watchbox.watchbox_transition_state
        WHERE watchbox_id = ? AND source_id = ? AND object_id = 'object-1'
        ORDER BY config_epoch
        """,
        String.class,
        WATCHBOX_ID,
        SOURCE_ID);
  }

  private static long detectionCount() {
    return jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_detection", Long.class);
  }
}
