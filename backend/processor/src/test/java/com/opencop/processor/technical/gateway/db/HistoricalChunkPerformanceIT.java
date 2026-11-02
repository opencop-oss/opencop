// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.model.mapper.FeatureMapper;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalReadPage;
import com.opencop.processor.business.model.history.HistoricalSampledPlaybackPoint;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.SourceObjectKey;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.business.policy.history.V1LatestInBucketSampler;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.flywaydb.core.Flyway;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

class HistoricalChunkPerformanceIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final int OBJECT_COUNT = 5_000;
  private static final int UPDATED_OBJECTS_PER_DELTA_CHUNK = 1_000;
  private static final int SKEW_TOTAL_EVENT_COUNT = 100_000;
  private static final int SKEW_EVENTS_PER_OBJECT = SKEW_TOTAL_EVENT_COUNT / OBJECT_COUNT;
  private static final int CHUNK_COUNT = 10;
  private static final int PAGE_SIZE = 500;
  private static final long PRE_PROD_VALIDATION_TARGET_MILLIS = 2_000L;
  private static final int BENCHMARK_MAX_PAYLOAD_PART_BYTES = 1_048_576;
  private static final int BENCHMARK_MAX_CHUNK_PAYLOAD_BYTES = 16_777_216;
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-04-06T10:00:00Z");
  private static final Instant EFFECTIVE_TO =
      EFFECTIVE_FROM.plus(HistoricalResolution.MINUTE_1.chunkSize().multipliedBy(CHUNK_COUNT));
  private static final Instant INITIAL_EVENT_TIME = EFFECTIVE_FROM.minusSeconds(30);
  private static final Instant STALE_AT = EFFECTIVE_TO.plusSeconds(300);
  private static final String SOURCE_IDS_NAMED_PARAMETER = "features.source IN (:sourceIds)";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FeatureMapper featureMapper = new FeatureMapper();

  @Test
  void benchmarkLiveHistoricalSourceWindow() {
    assumeTrue(
        Boolean.getBoolean("opencop.history.live.benchmark.enabled"),
        "Enable with -Dopencop.history.live.benchmark.enabled=true");

    UUID sourceId =
        UUID.fromString(
            System.getProperty(
                "opencop.history.live.benchmark.source-id",
                "bec14151-ef74-4dd5-9c6c-9d7673d0b9fa"));
    HistoricalResolution resolution =
        HistoricalResolution.valueOf(
            System.getProperty("opencop.history.live.benchmark.resolution", "DAY_1"));
    int pageSize = Integer.getInteger("opencop.history.live.benchmark.page-size", PAGE_SIZE);
    Instant effectiveTo =
        Instant.parse(
            System.getProperty("opencop.history.live.benchmark.to", Instant.now().toString()));
    Instant effectiveFrom =
        Instant.parse(
            System.getProperty(
                "opencop.history.live.benchmark.from",
                effectiveTo
                    .minus(Duration.ofDays(Long.getLong("opencop.history.live.benchmark.days", 7L)))
                    .toString()));

    DataSource dataSource = liveDataSource();
    DatasetShape windowShape = datasetShape(dataSource, sourceId, effectiveFrom, effectiveTo);
    HistoricalTracksJobSubmission job =
        liveJob(
            sourceId,
            effectiveFrom,
            effectiveTo,
            resolution,
            plannedChunks(effectiveFrom, effectiveTo, resolution));
    List<HistoricalChunkPlanEntry> chunks = new HistoricalChunkPlanner().planWindow(job).entries();
    HistoricalReadContext readContext =
        new HistoricalReadContext(List.of(sourceId), sourceId, true);
    HistoricalPostGisChunkReadGateway gateway =
        new HistoricalPostGisChunkReadGateway(
            new NamedParameterJdbcTemplate(dataSource),
            new DataSourceTransactionManager(dataSource),
            new HistoricalObservability(new SimpleMeterRegistry()),
            new HistoricalExecutionProperties(),
            io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
    HistoricalChunkPayloadBuilder payloadBuilder =
        HistoricalChunkPayloadBuilder.create(
            objectMapper,
            new HistoricalCacheIdentity(objectMapper),
            BENCHMARK_MAX_PAYLOAD_PART_BYTES,
            BENCHMARK_MAX_CHUNK_PAYLOAD_BYTES);
    LiveBenchmarkReport report =
        runLiveBenchmark(job, chunks, readContext, gateway, payloadBuilder, pageSize);

    printLiveReport(
        sourceId, effectiveFrom, effectiveTo, resolution, pageSize, windowShape, report);
  }

  @Test
  void benchmarkLiveHistoricalSourceWindowStreamingDelta() {
    assumeTrue(
        Boolean.getBoolean("opencop.history.live.streaming.benchmark.enabled"),
        "Enable with -Dopencop.history.live.streaming.benchmark.enabled=true");

    UUID sourceId =
        UUID.fromString(
            System.getProperty(
                "opencop.history.live.benchmark.source-id",
                "bec14151-ef74-4dd5-9c6c-9d7673d0b9fa"));
    HistoricalResolution resolution =
        HistoricalResolution.valueOf(
            System.getProperty("opencop.history.live.benchmark.resolution", "DAY_1"));
    int fetchSize = Integer.getInteger("opencop.history.live.benchmark.fetch-size", 5000);
    Instant effectiveTo =
        Instant.parse(
            System.getProperty("opencop.history.live.benchmark.to", Instant.now().toString()));
    Instant effectiveFrom =
        Instant.parse(
            System.getProperty(
                "opencop.history.live.benchmark.from",
                effectiveTo
                    .minus(Duration.ofDays(Long.getLong("opencop.history.live.benchmark.days", 7L)))
                    .toString()));

    DataSource dataSource = liveDataSource();
    DatasetShape windowShape = datasetShape(dataSource, sourceId, effectiveFrom, effectiveTo);
    HistoricalTracksJobSubmission job =
        liveJob(
            sourceId,
            effectiveFrom,
            effectiveTo,
            resolution,
            plannedChunks(effectiveFrom, effectiveTo, resolution));
    List<HistoricalChunkPlanEntry> chunks = new HistoricalChunkPlanner().planWindow(job).entries();
    HistoricalReadContext readContext =
        new HistoricalReadContext(List.of(sourceId), sourceId, true);
    HistoricalPostGisChunkReadGateway gateway =
        new HistoricalPostGisChunkReadGateway(
            new NamedParameterJdbcTemplate(dataSource),
            new DataSourceTransactionManager(dataSource),
            new HistoricalObservability(new SimpleMeterRegistry()),
            new HistoricalExecutionProperties(),
            io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
    HistoricalChunkPayloadBuilder payloadBuilder =
        HistoricalChunkPayloadBuilder.create(
            objectMapper,
            new HistoricalCacheIdentity(objectMapper),
            BENCHMARK_MAX_PAYLOAD_PART_BYTES,
            BENCHMARK_MAX_CHUNK_PAYLOAD_BYTES);
    LiveBenchmarkReport report =
        runLiveBenchmarkStreamingDelta(
            job, chunks, readContext, gateway, dataSource, payloadBuilder, fetchSize);

    printLiveReport(
        sourceId, effectiveFrom, effectiveTo, resolution, fetchSize, windowShape, report);
  }

  @Test
  void benchmarkHistoricalChunkPipeline() throws Exception {
    assumeTrue(
        Boolean.getBoolean("opencop.history.benchmark.enabled"),
        "Enable with -Dopencop.history.benchmark.enabled=true");

    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-44665544b000");

      BenchmarkReport baselineReport =
          runScenario(
              dataSource,
              sourceId,
              seedBaselineDataset(dataSource, sourceId),
              "baseline-5000-events-5000-objects");
      truncateFeatures(dataSource);
      BenchmarkReport skewReport =
          runScenario(
              dataSource,
              sourceId,
              seedSkewDataset(dataSource, sourceId),
              "skew-100000-events-5000-objects",
              new HistoricalReadContext(List.of(sourceId), sourceId, false, null, false));
      BenchmarkReport skewHelperReport =
          runScenario(
              dataSource,
              sourceId,
              datasetShape(dataSource),
              "skew-100000-events-5000-objects-helper-routing",
              prepareHelperBenchmarkState(dataSource, sourceId));
      BenchmarkReport skewCheckpointReport =
          runScenario(
              dataSource,
              sourceId,
              datasetShape(dataSource),
              "skew-100000-events-5000-objects-checkpoint-routing",
              prepareCheckpointBenchmarkState(dataSource, sourceId));
      BenchmarkReport layerHelperReport =
          runScenario(
              dataSource,
              sourceId,
              datasetShape(dataSource),
              "layer-100000-events-5000-objects-helper-routing",
              new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false),
              layerJob(sourceId));
      printReport("baseline-5000-events-5000-objects", baselineReport);
      printReport("skew-100000-events-5000-objects", skewReport);
      printReport("skew-100000-events-5000-objects-helper-routing", skewHelperReport);
      printReport("skew-100000-events-5000-objects-checkpoint-routing", skewCheckpointReport);
      printReport("layer-100000-events-5000-objects-helper-routing", layerHelperReport);
    }
  }

  @Test
  void validateWindowDrivenStateDeltaQueryOnPreProdShapedFixture() throws Exception {
    assumeTrue(
        Boolean.getBoolean("opencop.history.state-delta.validation.enabled"),
        "Enable with -Dopencop.history.state-delta.validation.enabled=true");

    int activeObjectCount =
        Integer.getInteger("opencop.history.state-delta.validation.active-objects", 50_000);
    int updatedObjectsPerChunk =
        Integer.getInteger("opencop.history.state-delta.validation.updated-per-chunk", 250);
    int historyRowsPerObject =
        Integer.getInteger("opencop.history.state-delta.validation.history-rows-per-object", 3);
    int chunkCount =
        Integer.getInteger("opencop.history.state-delta.validation.chunks", CHUNK_COUNT);
    int fetchSize = Integer.getInteger("opencop.history.state-delta.validation.fetch-size", 5_000);
    long targetMillis =
        Long.getLong(
            "opencop.history.state-delta.validation.target-millis",
            PRE_PROD_VALIDATION_TARGET_MILLIS);

    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-44665544d000");
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      seedPreProdShapedStateDeltaFixture(
          jdbc,
          sourceId,
          activeObjectCount,
          updatedObjectsPerChunk,
          historyRowsPerObject,
          chunkCount);
      prepareHelperBenchmarkState(dataSource, sourceId);

      HistoricalPostGisChunkReadGateway gateway =
          new HistoricalPostGisChunkReadGateway(
              new NamedParameterJdbcTemplate(dataSource),
              new DataSourceTransactionManager(dataSource),
              new HistoricalObservability(new SimpleMeterRegistry()),
              new HistoricalExecutionProperties(),
              io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
      HistoricalReadContext readContext =
          new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      HistoricalTracksJobSubmission job =
          job(sourceId, EFFECTIVE_FROM, EFFECTIVE_FROM.plusSeconds(60L * chunkCount), chunkCount);
      List<HistoricalChunkPlanEntry> chunks =
          new HistoricalChunkPlanner().planWindow(job).entries();

      StateDeltaValidationReport report =
          measureStateDeltaReads(
              gateway,
              readContext,
              job,
              chunks,
              fetchSize,
              activeObjectCount,
              updatedObjectsPerChunk);
      printStateDeltaValidationReport(report, targetMillis);
      printStateDeltaIndexEvidence(jdbc);
      printStateDeltaExplainEvidence(jdbc, sourceId, chunks.get(1));

      assertTrue(
          report.p95Millis() < targetMillis,
          "Expected readStateDelta P95 below %sms but was %sms"
              .formatted(targetMillis, report.p95Millis()));
    }
  }

  private BenchmarkReport runScenario(
      DataSource dataSource,
      UUID sourceId,
      DatasetShape datasetShape,
      String scenarioName,
      HistoricalReadContext readContext) {
    return runScenario(
        dataSource, sourceId, datasetShape, scenarioName, readContext, job(sourceId));
  }

  private BenchmarkReport runScenario(
      DataSource dataSource,
      UUID sourceId,
      DatasetShape datasetShape,
      String scenarioName,
      HistoricalReadContext readContext,
      HistoricalTracksJobSubmission job) {
    List<HistoricalChunkPlanEntry> chunks = new HistoricalChunkPlanner().planWindow(job).entries();

    HistoricalPostGisChunkReadGateway gatewayImpl =
        new HistoricalPostGisChunkReadGateway(
            new NamedParameterJdbcTemplate(dataSource),
            new DataSourceTransactionManager(dataSource),
            new HistoricalObservability(new SimpleMeterRegistry()),
            new HistoricalExecutionProperties(),
            io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
    MeasuringGateway gateway = new MeasuringGateway(gatewayImpl);
    HistoricalChunkPayloadBuilder payloadBuilder =
        HistoricalChunkPayloadBuilder.create(
            objectMapper,
            new HistoricalCacheIdentity(objectMapper),
            BENCHMARK_MAX_PAYLOAD_PART_BYTES,
            BENCHMARK_MAX_CHUNK_PAYLOAD_BYTES);
    V1LatestInBucketSampler sampler = new V1LatestInBucketSampler();
    BenchmarkRunner runner = new BenchmarkRunner(gateway, payloadBuilder, sampler);

    runner.run(job, chunks, readContext); // warm-up
    return runner
        .run(job, chunks, readContext)
        .withDatasetShape(datasetShape)
        .withScenarioName(scenarioName);
  }

  private BenchmarkReport runScenario(
      DataSource dataSource, UUID sourceId, DatasetShape datasetShape, String scenarioName) {
    return runScenario(
        dataSource,
        sourceId,
        datasetShape,
        scenarioName,
        new HistoricalReadContext(List.of(sourceId), sourceId, false, null, false));
  }

  private DatasetShape datasetShape(DataSource dataSource) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Long datasetRows = jdbc.queryForObject("SELECT COUNT(*) FROM geo.features", Long.class);
    Integer uniqueObjects =
        jdbc.queryForObject("SELECT COUNT(DISTINCT object_id) FROM geo.features", Integer.class);
    return new DatasetShape(
        datasetRows == null ? 0L : datasetRows, uniqueObjects == null ? 0 : uniqueObjects);
  }

  private DatasetShape datasetShape(
      DataSource dataSource, UUID sourceId, Instant effectiveFrom, Instant effectiveTo) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Long datasetRows =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM geo.features
            WHERE source = ?
              AND COALESCE(start_at, created_at) >= ?
              AND COALESCE(start_at, created_at) < ?
            """,
            Long.class,
            sourceId,
            Timestamp.from(effectiveFrom),
            Timestamp.from(effectiveTo));
    Integer uniqueObjects =
        jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT object_id)
            FROM geo.features
            WHERE source = ?
              AND COALESCE(start_at, created_at) >= ?
              AND COALESCE(start_at, created_at) < ?
            """,
            Integer.class,
            sourceId,
            Timestamp.from(effectiveFrom),
            Timestamp.from(effectiveTo));
    return new DatasetShape(
        datasetRows == null ? 0L : datasetRows, uniqueObjects == null ? 0 : uniqueObjects);
  }

  private void printReport(String scenarioName, BenchmarkReport report) {
    System.out.println("historical-benchmark scenario=" + scenarioName);
    System.out.println("historical-benchmark datasetRows=" + report.datasetRows());
    System.out.println("historical-benchmark uniqueObjects=" + report.uniqueObjects());
    System.out.println("historical-benchmark snapshotMillis=" + report.snapshotMillis());
    System.out.println("historical-benchmark deltaMillis=" + report.deltaMillis());
    System.out.println("historical-benchmark renderMillis=" + report.renderMillis());
    System.out.println("historical-benchmark payloadMillis=" + report.payloadMillis());
    System.out.println("historical-benchmark averageChunkMillis=" + report.averageChunkMillis());
    System.out.println("historical-benchmark maxChunkMillis=" + report.maxChunkMillis());
    System.out.println("historical-benchmark sampledPoints=" + report.sampledPoints());
    System.out.println("historical-benchmark activeObjects=" + report.activeObjects());
  }

  private void printLiveReport(
      UUID sourceId,
      Instant effectiveFrom,
      Instant effectiveTo,
      HistoricalResolution resolution,
      int pageSize,
      DatasetShape windowShape,
      LiveBenchmarkReport report) {
    System.out.println("live-historical-benchmark sourceId=" + sourceId);
    System.out.println("live-historical-benchmark from=" + effectiveFrom);
    System.out.println("live-historical-benchmark to=" + effectiveTo);
    System.out.println("live-historical-benchmark resolution=" + resolution);
    System.out.println("live-historical-benchmark pageSize=" + pageSize);
    System.out.println("live-historical-benchmark windowRows=" + windowShape.datasetRows());
    System.out.println("live-historical-benchmark windowObjects=" + windowShape.uniqueObjects());
    System.out.println("live-historical-benchmark chunks=" + report.chunks());
    System.out.println("live-historical-benchmark snapshotRows=" + report.snapshotRows());
    System.out.println("live-historical-benchmark deltaRows=" + report.deltaRows());
    System.out.println("live-historical-benchmark snapshotPages=" + report.snapshotPages());
    System.out.println("live-historical-benchmark deltaPages=" + report.deltaPages());
    System.out.println("live-historical-benchmark readMillis=" + report.readMillis());
    System.out.println("live-historical-benchmark sampleMillis=" + report.sampleMillis());
    System.out.println("live-historical-benchmark renderMillis=" + report.renderMillis());
    System.out.println("live-historical-benchmark payloadMillis=" + report.payloadMillis());
    System.out.println("live-historical-benchmark totalMillis=" + report.totalMillis());
    System.out.println("live-historical-benchmark maxChunkMillis=" + report.maxChunkMillis());
    for (LiveChunkReport chunk : report.chunkReports()) {
      System.out.println(
          "live-historical-benchmark chunk index=%s from=%s to=%s readPath=%s rows=%s pages=%s activeObjects=%s sampledPoints=%s readMillis=%s sampleMillis=%s renderMillis=%s payloadMillis=%s totalMillis=%s"
              .formatted(
                  chunk.chunkIndex(),
                  chunk.from(),
                  chunk.to(),
                  chunk.readPath(),
                  chunk.rows(),
                  chunk.pages(),
                  chunk.activeObjects(),
                  chunk.sampledPoints(),
                  chunk.readMillis(),
                  chunk.sampleMillis(),
                  chunk.renderMillis(),
                  chunk.payloadMillis(),
                  chunk.totalMillis()));
    }
  }

  private DatasetShape seedBaselineDataset(DataSource dataSource, UUID sourceId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<RowSeed> rows =
        new ArrayList<>(OBJECT_COUNT + ((CHUNK_COUNT - 1) * UPDATED_OBJECTS_PER_DELTA_CHUNK));

    for (int objectIndex = 0; objectIndex < OBJECT_COUNT; objectIndex++) {
      String objectId = objectId(objectIndex);
      rows.add(
          new RowSeed(
              UUID.nameUUIDFromBytes(
                  ("initial-" + objectId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
              sourceId,
              objectId,
              INITIAL_EVENT_TIME,
              STALE_AT,
              objectIndex,
              0));
    }

    for (int chunkIndex = 1; chunkIndex < CHUNK_COUNT; chunkIndex++) {
      Instant chunkStart =
          EFFECTIVE_FROM.plus(HistoricalResolution.MINUTE_1.chunkSize().multipliedBy(chunkIndex));
      for (int updatedIndex = 0; updatedIndex < UPDATED_OBJECTS_PER_DELTA_CHUNK; updatedIndex++) {
        int objectIndex =
            ((chunkIndex - 1) * UPDATED_OBJECTS_PER_DELTA_CHUNK + updatedIndex) % OBJECT_COUNT;
        String objectId = objectId(objectIndex);
        Instant eventTime = chunkStart.plusSeconds(5 + (updatedIndex % 30));
        rows.add(
            new RowSeed(
                UUID.nameUUIDFromBytes(
                    ("delta-" + chunkIndex + '-' + objectId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sourceId,
                objectId,
                eventTime,
                STALE_AT,
                objectIndex,
                chunkIndex));
      }
    }

    insertRows(jdbc, rows);
    return new DatasetShape(rows.size(), OBJECT_COUNT);
  }

  private DatasetShape seedSkewDataset(DataSource dataSource, UUID sourceId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<RowSeed> rows = new ArrayList<>(SKEW_TOTAL_EVENT_COUNT);

    for (int objectIndex = 0; objectIndex < OBJECT_COUNT; objectIndex++) {
      String objectId = objectId(objectIndex);
      for (int eventIndex = 0; eventIndex < SKEW_EVENTS_PER_OBJECT; eventIndex++) {
        Instant eventTime =
            EFFECTIVE_FROM
                .minusSeconds((long) (SKEW_EVENTS_PER_OBJECT - eventIndex) * 45L)
                .plusMillis(objectIndex);
        rows.add(
            new RowSeed(
                UUID.nameUUIDFromBytes(
                    ("skew-" + objectId + '-' + eventIndex)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sourceId,
                objectId,
                eventTime,
                STALE_AT,
                objectIndex,
                -1));
      }
    }

    insertRows(jdbc, rows);
    return new DatasetShape(rows.size(), OBJECT_COUNT);
  }

  private void seedPreProdShapedStateDeltaFixture(
      JdbcTemplate jdbc,
      UUID sourceId,
      int activeObjectCount,
      int updatedObjectsPerChunk,
      int historyRowsPerObject,
      int chunkCount) {
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, display_name, username)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        "benchmark-user",
        "benchmark-user",
        "benchmark",
        "Benchmark User",
        "benchmark-user");
    jdbc.update(
        """
        INSERT INTO geo.features
          (id, feature_kind, source, author_id, object_id, created_at, start_at, stale_at, geom, payload)
        SELECT
          gen_random_uuid(),
          'TRACK',
          ?,
          '550e8400-e29b-41d4-a716-446655440120'::uuid,
          'validation-object-' || object_idx,
          ?::timestamptz - (history_idx * INTERVAL '30 seconds') + (object_idx * INTERVAL '1 millisecond'),
          ?::timestamptz - (history_idx * INTERVAL '30 seconds') + (object_idx * INTERVAL '1 millisecond'),
          ?::timestamptz,
          '{"type":"Point","coordinates":[-73.5,45.5]}',
          jsonb_build_object(
            'type', 'Feature',
            'properties', jsonb_build_object(
              'opencop', jsonb_build_object(
                'id', gen_random_uuid()::text,
                'objectId', 'validation-object-' || object_idx,
                'source', ?::text,
                'displayName', 'Validation Object ' || object_idx,
                'timestampz', jsonb_build_object(
                  'time', floor(extract(epoch from (?::timestamptz - (history_idx * INTERVAL '30 seconds'))) * 1000),
                  'start', floor(extract(epoch from (?::timestamptz - (history_idx * INTERVAL '30 seconds'))) * 1000),
                  'stale', floor(extract(epoch from ?::timestamptz) * 1000)
                ),
                'element', jsonb_build_object('type', 'a-f-G-U-C', 'speed', 12.5, 'remarks', 'initial-history')
              )
            )
          )
        FROM generate_series(1, ?) object_idx
        CROSS JOIN generate_series(1, ?) history_idx
        """,
        sourceId,
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM.plusSeconds(60L * chunkCount + 300L)),
        sourceId,
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM.plusSeconds(60L * chunkCount + 300L)),
        activeObjectCount,
        historyRowsPerObject);
    jdbc.update(
        """
        INSERT INTO geo.features
          (id, feature_kind, source, author_id, object_id, created_at, start_at, stale_at, geom, payload)
        SELECT
          gen_random_uuid(),
          'TRACK',
          ?,
          '550e8400-e29b-41d4-a716-446655440120'::uuid,
          'validation-object-' || (((chunk_idx - 1) * ? + update_idx - 1) % ?) + 1,
          ?::timestamptz + (chunk_idx * INTERVAL '1 minute') + (update_idx * INTERVAL '1 millisecond'),
          ?::timestamptz + (chunk_idx * INTERVAL '1 minute') + (update_idx * INTERVAL '1 millisecond'),
          ?::timestamptz,
          '{"type":"Point","coordinates":[-73.5,45.5]}',
          jsonb_build_object(
            'type', 'Feature',
            'properties', jsonb_build_object(
              'opencop', jsonb_build_object(
                'id', gen_random_uuid()::text,
                'objectId', 'validation-object-' || (((chunk_idx - 1) * ? + update_idx - 1) % ?) + 1,
                'source', ?::text,
                'displayName', 'Validation Object ' || update_idx,
                'timestampz', jsonb_build_object(
                  'time', floor(extract(epoch from (?::timestamptz + (chunk_idx * INTERVAL '1 minute'))) * 1000),
                  'start', floor(extract(epoch from (?::timestamptz + (chunk_idx * INTERVAL '1 minute'))) * 1000),
                  'stale', floor(extract(epoch from ?::timestamptz) * 1000)
                ),
                'element', jsonb_build_object('type', 'a-f-G-U-C', 'speed', 12.5, 'remarks', 'sparse-update')
              )
            )
          )
        FROM generate_series(1, ?) chunk_idx
        CROSS JOIN generate_series(1, ?) update_idx
        """,
        sourceId,
        updatedObjectsPerChunk,
        activeObjectCount,
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM.plusSeconds(60L * chunkCount + 300L)),
        updatedObjectsPerChunk,
        activeObjectCount,
        sourceId,
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM),
        Timestamp.from(EFFECTIVE_FROM.plusSeconds(60L * chunkCount + 300L)),
        chunkCount - 1,
        updatedObjectsPerChunk);
  }

  private void insertRows(JdbcTemplate jdbc, List<RowSeed> rows) {
    try {
      UUID authorId = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
      jdbc.update(
          """
          INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, display_name, username)
          VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          authorId,
          "benchmark-user",
          "benchmark-user",
          "benchmark",
          "Benchmark User",
          "benchmark-user");

      jdbc.batchUpdate(
          """
          INSERT INTO geo.features
            (id, feature_kind, source, author_id, object_id, created_at, start_at, stale_at, geom, payload)
          VALUES
            (?, 'TRACK', ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
          """,
          rows,
          500,
          (ps, row) -> {
            ps.setObject(1, row.id());
            ps.setObject(2, row.sourceId());
            ps.setObject(3, authorId);
            ps.setString(4, row.objectId());
            ps.setTimestamp(5, java.sql.Timestamp.from(row.eventTime()));
            ps.setTimestamp(6, java.sql.Timestamp.from(row.eventTime()));
            ps.setTimestamp(7, java.sql.Timestamp.from(row.staleAt()));
            ps.setString(8, geometryJson(row));
            ps.setString(9, payloadJson(row));
          });
    } catch (Exception e) {
      throw new IllegalStateException("Failed to seed benchmark data through JdbcTemplate", e);
    }
  }

  private void truncateFeatures(DataSource dataSource) {
    new JdbcTemplate(dataSource).execute("TRUNCATE TABLE geo.features");
  }

  private HistoricalReadContext prepareHelperBenchmarkState(DataSource dataSource, UUID sourceId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE geo.feature_latest_active_helper");
    jdbc.update(
        """
        INSERT INTO geo.feature_latest_active_helper
          (source, object_id, event_id, playback_at, first_emission_at, created_at, stale_at)
        SELECT
          latest.source,
          latest.object_id,
          latest.id,
          latest.playback_at,
          first_emission.first_emission_at,
          latest.created_at,
          latest.stale_at
        FROM (
          SELECT DISTINCT ON (source, object_id)
            source,
            object_id,
            id,
            COALESCE(start_at, created_at) AS playback_at,
            created_at,
            stale_at
          FROM geo.features
          WHERE source = ?
          ORDER BY source, object_id, COALESCE(start_at, created_at) DESC, created_at DESC, id DESC
        ) latest
        JOIN (
          SELECT source, object_id, MIN(COALESCE(start_at, created_at)) AS first_emission_at
          FROM geo.features
          WHERE source = ?
          GROUP BY source, object_id
        ) first_emission
          ON first_emission.source = latest.source
         AND first_emission.object_id = latest.object_id
        """,
        sourceId,
        sourceId);
    return new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
  }

  private HistoricalReadContext prepareCheckpointBenchmarkState(
      DataSource dataSource, UUID sourceId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE geo.feature_latest_active_helper");
    jdbc.execute("TRUNCATE TABLE geo.feature_history_checkpoint");
    prepareHelperBenchmarkState(dataSource, sourceId);
    Instant checkpointAt = EFFECTIVE_FROM;
    jdbc.update(
        """
        INSERT INTO geo.feature_history_checkpoint
          (source, object_id, resolution, checkpoint_at, event_id, playback_at, created_at, stale_at)
        SELECT DISTINCT ON (features.object_id)
          features.source,
          features.object_id,
          ?,
          ?,
          features.id,
          COALESCE(features.start_at, features.created_at) AS playback_at,
          features.created_at,
          features.stale_at
        FROM geo.features features
        WHERE features.source = ?
          AND COALESCE(features.start_at, features.created_at) < ?
          AND (features.stale_at IS NULL OR features.stale_at > ?)
        ORDER BY features.object_id, COALESCE(features.start_at, features.created_at) DESC, features.created_at DESC, features.id DESC
        """,
        HistoricalResolution.MINUTE_1.name(),
        java.sql.Timestamp.from(checkpointAt),
        sourceId,
        java.sql.Timestamp.from(checkpointAt),
        java.sql.Timestamp.from(checkpointAt));
    return new HistoricalReadContext(
        List.of(sourceId), sourceId, false, HistoricalResolution.MINUTE_1, true);
  }

  private String payloadJson(RowSeed row) {
    long eventMillis = row.eventTime().toEpochMilli();
    double lon = -73.5 + ((row.objectIndex() % 100) * 0.001);
    double lat = 45.5 + ((row.objectIndex() / 100) * 0.001);
    return """
        {
          "type": "Feature",
          "geometry": {
            "type": "Point",
            "coordinates": [%s, %s]
          },
          "properties": {
            "opencop": {
              "id": "%s",
              "objectId": "%s",
              "source": "%s",
              "displayName": "Benchmark Object %s",
              "timestampz": {
                "time": %s,
                "start": %s,
                "stale": %s
              },
              "element": {
                "type": "a-f-G-U-C",
                "speed": 12.5,
                "remarks": "chunk-%s"
              }
            }
          }
        }
        """
        .formatted(
            lon,
            lat,
            row.id(),
            row.objectId(),
            row.sourceId(),
            row.objectId(),
            eventMillis,
            eventMillis,
            row.staleAt().toEpochMilli(),
            row.chunkIndex());
  }

  private String geometryJson(RowSeed row) {
    double lon = -73.5 + ((row.objectIndex() % 100) * 0.001);
    double lat = 45.5 + ((row.objectIndex() / 100) * 0.001);
    return """
        {"type":"Point","coordinates":[%s,%s]}
        """
        .formatted(lon, lat);
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration", sdkMigrationLocation())
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static String sdkMigrationLocation() {
    return "filesystem:"
        + Path.of("..", "sdk", "src", "main", "resources", "db", "migration").toAbsolutePath();
  }

  private static HistoricalTracksJobSubmission job(UUID sourceId) {
    return job(sourceId, EFFECTIVE_FROM, EFFECTIVE_TO, CHUNK_COUNT);
  }

  private static HistoricalTracksJobSubmission job(
      UUID sourceId, Instant effectiveFrom, Instant effectiveTo, long plannedChunks) {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-44665544c000"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-44665544c001")),
        SubjectType.SOURCE,
        sourceId,
        UUID.fromString("550e8400-e29b-41d4-a716-44665544c001"),
        effectiveTo,
        effectiveFrom,
        effectiveTo,
        effectiveFrom,
        effectiveTo,
        HistoricalResolution.MINUTE_1,
        plannedChunks,
        List.of());
  }

  private static HistoricalTracksJobSubmission layerJob(UUID layerId) {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-44665544c100"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-44665544c001")),
        SubjectType.LAYER,
        layerId,
        UUID.fromString("550e8400-e29b-41d4-a716-44665544c001"),
        EFFECTIVE_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        HistoricalResolution.MINUTE_1,
        CHUNK_COUNT,
        List.of());
  }

  private static HistoricalTracksJobSubmission liveJob(
      UUID sourceId,
      Instant effectiveFrom,
      Instant effectiveTo,
      HistoricalResolution resolution,
      long plannedChunks) {
    UUID requestedBy = UUID.fromString("550e8400-e29b-41d4-a716-44665544c001");
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(requestedBy),
        SubjectType.SOURCE,
        sourceId,
        requestedBy,
        effectiveTo,
        effectiveFrom,
        effectiveTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        List.of());
  }

  private static long plannedChunks(
      Instant effectiveFrom, Instant effectiveTo, HistoricalResolution resolution) {
    long windowSeconds = Duration.between(effectiveFrom, effectiveTo).getSeconds();
    long chunkSeconds = resolution.chunkSize().getSeconds();
    return (windowSeconds + chunkSeconds - 1) / chunkSeconds;
  }

  private StateDeltaValidationReport measureStateDeltaReads(
      HistoricalPostGisChunkReadGateway gateway,
      HistoricalReadContext readContext,
      HistoricalTracksJobSubmission job,
      List<HistoricalChunkPlanEntry> chunks,
      int fetchSize,
      int activeObjectCount,
      int updatedObjectsPerChunk) {
    List<Long> readMillisByChunk = new ArrayList<>();
    List<Long> rowsByChunk = new ArrayList<>();
    Instant previousChunkEndExclusive = chunks.getFirst().to();

    for (int chunkIndex = 1; chunkIndex < chunks.size(); chunkIndex++) {
      HistoricalChunkPlanEntry chunk = chunks.get(chunkIndex);
      long[] rows = {0L};
      long startNanos = System.nanoTime();
      gateway.streamLatestStateDelta(
          readContext,
          HistoricalExecutionContext.from(job),
          chunk,
          previousChunkEndExclusive,
          fetchSize,
          _ -> rows[0]++);
      readMillisByChunk.add(nanosToMillis(System.nanoTime() - startNanos));
      rowsByChunk.add(rows[0]);
      previousChunkEndExclusive = chunk.to();
    }

    return new StateDeltaValidationReport(
        activeObjectCount,
        updatedObjectsPerChunk,
        chunks.size() - 1,
        readMillisByChunk,
        rowsByChunk,
        percentileMillis(readMillisByChunk, 0.95d));
  }

  private void printStateDeltaValidationReport(
      StateDeltaValidationReport report, long targetMillis) {
    System.out.println("state-delta-validation activeObjects=" + report.activeObjectCount());
    System.out.println(
        "state-delta-validation updatedObjectsPerChunk=" + report.updatedObjectsPerChunk());
    System.out.println("state-delta-validation measuredChunks=" + report.measuredChunks());
    System.out.println("state-delta-validation readMillisByChunk=" + report.readMillisByChunk());
    System.out.println("state-delta-validation rowsByChunk=" + report.rowsByChunk());
    System.out.println("state-delta-validation p95Millis=" + report.p95Millis());
    System.out.println("state-delta-validation targetMillis=" + targetMillis);
  }

  private void printStateDeltaIndexEvidence(JdbcTemplate jdbc) {
    System.out.println("state-delta-validation pg_indexes:");
    jdbc.queryForList(
            """
            SELECT schemaname, tablename, indexname, indexdef
            FROM pg_indexes
            WHERE schemaname = 'geo'
              AND tablename IN ('features', 'feature_latest_active_helper')
            ORDER BY tablename, indexname
            """)
        .forEach(row -> System.out.println("state-delta-validation pg_indexes row=" + row));
  }

  private void printStateDeltaExplainEvidence(
      JdbcTemplate jdbc, UUID sourceId, HistoricalChunkPlanEntry chunk) {
    System.out.println("state-delta-validation explainAnalyzeBuffers:");
    jdbc.queryForList(
            stateDeltaExplainSqlForJdbc(),
            sourceId,
            Timestamp.from(chunk.from()),
            Timestamp.from(chunk.to()),
            Timestamp.from(chunk.from()),
            Timestamp.from(chunk.to()))
        .forEach(row -> System.out.println("state-delta-validation explain row=" + row));
    printManualStateDeltaValidationSql(sourceId, chunk);
  }

  private void printManualStateDeltaValidationSql(UUID sourceId, HistoricalChunkPlanEntry chunk) {
    System.out.println("state-delta-validation manual pg_indexes SQL:");
    System.out.println(
        """
        SELECT schemaname, tablename, indexname, indexdef
        FROM pg_indexes
        WHERE schemaname = 'geo'
          AND tablename IN ('features', 'feature_latest_active_helper')
        ORDER BY tablename, indexname;
        """);
    System.out.println("state-delta-validation manual EXPLAIN SQL:");
    System.out.println(stateDeltaExplainSqlForManual(sourceId, chunk));
  }

  private static String stateDeltaExplainSqlForJdbc() {
    return "EXPLAIN (ANALYZE, BUFFERS) "
        + productionStateDeltaSqlForSingleSource().replace(":from", "?").replace(":to", "?");
  }

  private static String stateDeltaExplainSqlForManual(
      UUID sourceId, HistoricalChunkPlanEntry chunk) {
    return "EXPLAIN (ANALYZE, BUFFERS) "
        + productionStateDeltaSqlForSingleSource()
            .replace("features.source = ?", "features.source = '%s'::uuid".formatted(sourceId))
            .replace(":from", "'%s'::timestamptz".formatted(chunk.from()))
            .replace(":to", "'%s'::timestamptz".formatted(chunk.to()));
  }

  private static String productionStateDeltaSqlForSingleSource() {
    return PostGisHistoricalReadSql.streamStateDeltaSql()
        .replace(SOURCE_IDS_NAMED_PARAMETER, "features.source = ?");
  }

  private static long percentileMillis(List<Long> values, double percentile) {
    if (values.isEmpty()) {
      return 0L;
    }
    List<Long> sorted = values.stream().sorted().toList();
    int index = (int) Math.ceil(percentile * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private static DataSource liveDataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(System.getProperty("opencop.db.driver", "org.postgresql.Driver"));
    dataSource.setUrl(
        System.getProperty("opencop.db.url", "jdbc:postgresql://localhost:5432/opencop-db"));
    dataSource.setUsername(System.getProperty("opencop.db.username", "admin"));
    dataSource.setPassword(System.getProperty("opencop.db.password", "mypassword"));
    return dataSource;
  }

  private static String objectId(int objectIndex) {
    return "bench-object-" + objectIndex;
  }

  private LiveBenchmarkReport runLiveBenchmark(
      HistoricalTracksJobSubmission job,
      List<HistoricalChunkPlanEntry> chunks,
      HistoricalReadContext readContext,
      HistoricalPostGisChunkReadGateway gateway,
      HistoricalChunkPayloadBuilder payloadBuilder,
      int pageSize) {
    V1LatestInBucketSampler sampler = new V1LatestInBucketSampler();
    Map<SourceObjectKey, HistoricalSourceEvent> activeState = new LinkedHashMap<>();
    List<LiveChunkReport> chunkReports = new ArrayList<>();
    Instant previousChunkEndExclusive = null;
    long totalReadNanos = 0L;
    long totalSampleNanos = 0L;
    long totalRenderNanos = 0L;
    long totalPayloadNanos = 0L;
    long totalChunkNanos = 0L;
    long maxChunkNanos = 0L;
    long snapshotRows = 0L;
    long deltaRows = 0L;
    long snapshotPages = 0L;
    long deltaPages = 0L;

    for (HistoricalChunkPlanEntry chunk : chunks) {
      long chunkStartNanos = System.nanoTime();
      LiveReadReport readReport;
      if (previousChunkEndExclusive == null) {
        readReport =
            accumulateLiveSnapshot(gateway, readContext, job, chunk, pageSize, activeState);
        snapshotRows += readReport.rows();
        snapshotPages += readReport.pages();
      } else {
        readReport =
            accumulateLiveDelta(
                gateway, readContext, job, chunk, previousChunkEndExclusive, pageSize, activeState);
        deltaRows += readReport.rows();
        deltaPages += readReport.pages();
      }
      totalReadNanos += readReport.nanos();

      pruneInactive(activeState, chunk.from());

      long sampleStartNanos = System.nanoTime();
      var sampledChunk = sampler.sampleChunk(chunk, job.resolution(), activeState.values());
      long sampleNanos = System.nanoTime() - sampleStartNanos;
      totalSampleNanos += sampleNanos;

      long renderStartNanos = System.nanoTime();
      measureRenderPreparation(sampledChunk.points());
      long renderNanos = System.nanoTime() - renderStartNanos;
      totalRenderNanos += renderNanos;

      long payloadStartNanos = System.nanoTime();
      payloadBuilder.buildPayload(sampledChunk, payloadKeyFor(job, chunk.from()));
      long payloadNanos = System.nanoTime() - payloadStartNanos;
      totalPayloadNanos += payloadNanos;

      long chunkNanos = System.nanoTime() - chunkStartNanos;
      totalChunkNanos += chunkNanos;
      maxChunkNanos = Math.max(maxChunkNanos, chunkNanos);
      chunkReports.add(
          new LiveChunkReport(
              chunk.chunkIndex(),
              chunk.from(),
              chunk.to(),
              readReport.readPath(),
              readReport.rows(),
              readReport.pages(),
              activeState.size(),
              sampledChunk.pointCount(),
              nanosToMillis(readReport.nanos()),
              nanosToMillis(sampleNanos),
              nanosToMillis(renderNanos),
              nanosToMillis(payloadNanos),
              nanosToMillis(chunkNanos)));
      previousChunkEndExclusive = chunk.to();
    }

    return new LiveBenchmarkReport(
        chunks.size(),
        snapshotRows,
        deltaRows,
        snapshotPages,
        deltaPages,
        nanosToMillis(totalReadNanos),
        nanosToMillis(totalSampleNanos),
        nanosToMillis(totalRenderNanos),
        nanosToMillis(totalPayloadNanos),
        nanosToMillis(totalChunkNanos),
        nanosToMillis(maxChunkNanos),
        chunkReports);
  }

  private LiveBenchmarkReport runLiveBenchmarkStreamingDelta(
      HistoricalTracksJobSubmission job,
      List<HistoricalChunkPlanEntry> chunks,
      HistoricalReadContext readContext,
      HistoricalPostGisChunkReadGateway gateway,
      DataSource dataSource,
      HistoricalChunkPayloadBuilder payloadBuilder,
      int fetchSize) {
    V1LatestInBucketSampler sampler = new V1LatestInBucketSampler();
    Map<SourceObjectKey, HistoricalSourceEvent> activeState = new LinkedHashMap<>();
    List<LiveChunkReport> chunkReports = new ArrayList<>();
    Instant previousChunkEndExclusive = null;
    long totalReadNanos = 0L;
    long totalSampleNanos = 0L;
    long totalRenderNanos = 0L;
    long totalPayloadNanos = 0L;
    long totalChunkNanos = 0L;
    long maxChunkNanos = 0L;
    long snapshotRows = 0L;
    long deltaRows = 0L;
    long snapshotPages = 0L;
    long deltaPages = 0L;

    for (HistoricalChunkPlanEntry chunk : chunks) {
      long chunkStartNanos = System.nanoTime();
      LiveReadReport readReport;
      if (previousChunkEndExclusive == null) {
        readReport =
            accumulateLiveSnapshot(gateway, readContext, job, chunk, fetchSize, activeState);
        snapshotRows += readReport.rows();
        snapshotPages += readReport.pages();
      } else {
        readReport =
            accumulateLiveDeltaStreaming(
                dataSource, readContext, chunk, previousChunkEndExclusive, fetchSize, activeState);
        deltaRows += readReport.rows();
        deltaPages += readReport.pages();
      }
      totalReadNanos += readReport.nanos();

      pruneInactive(activeState, chunk.from());

      long sampleStartNanos = System.nanoTime();
      var sampledChunk = sampler.sampleChunk(chunk, job.resolution(), activeState.values());
      long sampleNanos = System.nanoTime() - sampleStartNanos;
      totalSampleNanos += sampleNanos;

      long renderStartNanos = System.nanoTime();
      measureRenderPreparation(sampledChunk.points());
      long renderNanos = System.nanoTime() - renderStartNanos;
      totalRenderNanos += renderNanos;

      long payloadStartNanos = System.nanoTime();
      payloadBuilder.buildPayload(sampledChunk, payloadKeyFor(job, chunk.from()));
      long payloadNanos = System.nanoTime() - payloadStartNanos;
      totalPayloadNanos += payloadNanos;

      long chunkNanos = System.nanoTime() - chunkStartNanos;
      totalChunkNanos += chunkNanos;
      maxChunkNanos = Math.max(maxChunkNanos, chunkNanos);
      chunkReports.add(
          new LiveChunkReport(
              chunk.chunkIndex(),
              chunk.from(),
              chunk.to(),
              readReport.readPath(),
              readReport.rows(),
              readReport.pages(),
              activeState.size(),
              sampledChunk.pointCount(),
              nanosToMillis(readReport.nanos()),
              nanosToMillis(sampleNanos),
              nanosToMillis(renderNanos),
              nanosToMillis(payloadNanos),
              nanosToMillis(chunkNanos)));
      previousChunkEndExclusive = chunk.to();
    }

    return new LiveBenchmarkReport(
        chunks.size(),
        snapshotRows,
        deltaRows,
        snapshotPages,
        deltaPages,
        nanosToMillis(totalReadNanos),
        nanosToMillis(totalSampleNanos),
        nanosToMillis(totalRenderNanos),
        nanosToMillis(totalPayloadNanos),
        nanosToMillis(totalChunkNanos),
        nanosToMillis(maxChunkNanos),
        chunkReports);
  }

  private LiveReadReport accumulateLiveSnapshot(
      HistoricalPostGisChunkReadGateway gateway,
      HistoricalReadContext readContext,
      HistoricalTracksJobSubmission job,
      HistoricalChunkPlanEntry chunk,
      int pageSize,
      Map<SourceObjectKey, HistoricalSourceEvent> activeState) {
    HistoricalReadCursor cursor = null;
    HistoricalReadPage page;
    long rows = 0L;
    long pages = 0L;
    long readNanos = 0L;
    do {
      long startNanos = System.nanoTime();
      page =
          gateway.readActiveSnapshot(
              readContext, HistoricalExecutionContext.from(job), chunk, cursor, pageSize);
      readNanos += System.nanoTime() - startNanos;
      pages++;
      rows += page.rows().size();
      for (HistoricalSourceEvent event : page.rows()) {
        activeState.put(new SourceObjectKey(event.sourceId(), event.objectId()), event);
      }
      cursor = page.nextCursor();
    } while (page.hasMore());
    return new LiveReadReport("snapshot", rows, pages, readNanos);
  }

  private LiveReadReport accumulateLiveDelta(
      HistoricalPostGisChunkReadGateway gateway,
      HistoricalReadContext readContext,
      HistoricalTracksJobSubmission job,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      int pageSize,
      Map<SourceObjectKey, HistoricalSourceEvent> activeState) {
    HistoricalReadCursor cursor = null;
    HistoricalReadPage page;
    long rows = 0L;
    long pages = 0L;
    long readNanos = 0L;
    do {
      long startNanos = System.nanoTime();
      page =
          gateway.readDelta(
              readContext,
              HistoricalExecutionContext.from(job),
              chunk,
              previousChunkEndExclusive,
              cursor,
              pageSize);
      readNanos += System.nanoTime() - startNanos;
      pages++;
      rows += page.rows().size();
      for (HistoricalSourceEvent event : page.rows()) {
        activeState.put(new SourceObjectKey(event.sourceId(), event.objectId()), event);
      }
      cursor = page.nextCursor();
    } while (page.hasMore());
    return new LiveReadReport("delta", rows, pages, readNanos);
  }

  private LiveReadReport accumulateLiveDeltaStreaming(
      DataSource dataSource,
      HistoricalReadContext readContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      int fetchSize,
      Map<SourceObjectKey, HistoricalSourceEvent> activeState) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    long startNanos = System.nanoTime();
    long[] rows = {0L};
    jdbc.query(
        connection -> {
          connection.setReadOnly(true);
          connection.setAutoCommit(false);
          var statement =
              connection.prepareStatement(
                  """
                  SELECT
                    id,
                    source,
                    object_id,
                    created_at,
                    start_at,
                    stale_at,
                    payload::text AS payload_json,
                    COALESCE(start_at, created_at) AS playback_at
                  FROM geo.features
                  WHERE source = ?
                    AND COALESCE(start_at, created_at) >= ?
                    AND COALESCE(start_at, created_at) < ?
                  ORDER BY playback_at ASC, object_id ASC, id ASC
                  """);
          statement.setFetchSize(fetchSize);
          statement.setObject(1, readContext.singleSourceId());
          statement.setTimestamp(2, Timestamp.from(previousChunkEndExclusive));
          statement.setTimestamp(3, Timestamp.from(chunk.to()));
          return statement;
        },
        resultSet -> {
          HistoricalSourceEvent event = mapLiveRow(resultSet);
          activeState.put(new SourceObjectKey(event.sourceId(), event.objectId()), event);
          rows[0]++;
        });
    long readNanos = System.nanoTime() - startNanos;
    long fetchBatches = (rows[0] + fetchSize - 1L) / fetchSize;
    return new LiveReadReport("delta-stream", rows[0], Math.max(1L, fetchBatches), readNanos);
  }

  private static HistoricalSourceEvent mapLiveRow(ResultSet rs) throws SQLException {
    UUID eventId = rs.getObject("id", UUID.class);
    UUID sourceId = rs.getObject("source", UUID.class);
    String objectId = rs.getString("object_id");
    Instant playbackAt = rs.getTimestamp("playback_at").toInstant();
    Instant createdAt = rs.getTimestamp("created_at").toInstant();
    Timestamp startAtValue = rs.getTimestamp("start_at");
    Instant startAt = startAtValue == null ? null : startAtValue.toInstant();
    Timestamp staleAtValue = rs.getTimestamp("stale_at");
    Instant staleAt = staleAtValue == null ? null : staleAtValue.toInstant();
    String payloadJson = rs.getString("payload_json");
    return new HistoricalSourceEvent(
        eventId, sourceId, objectId, playbackAt, createdAt, startAt, staleAt, payloadJson);
  }

  private void measureRenderPreparation(List<HistoricalSampledPlaybackPoint> points) {
    for (HistoricalSampledPlaybackPoint point : points) {
      try {
        Feature feature = objectMapper.readValue(point.payloadJson(), Feature.class);
        ElementMessage elementMessage = featureMapper.toMessage(feature);
        if (elementMessage == null) {
          throw new IllegalStateException("FeatureMapper returned null for " + point.eventId());
        }
      } catch (Exception e) {
        throw new IllegalStateException("Failed to render benchmark point " + point.eventId(), e);
      }
    }
  }

  private static void pruneInactive(
      Map<SourceObjectKey, HistoricalSourceEvent> activeState, Instant chunkStartInclusive) {
    activeState
        .values()
        .removeIf(
            event -> event.staleAt() != null && !event.staleAt().isAfter(chunkStartInclusive));
  }

  private final class BenchmarkRunner {
    private final MeasuringGateway gateway;
    private final HistoricalChunkPayloadBuilder payloadBuilder;
    private final V1LatestInBucketSampler sampler;

    private BenchmarkRunner(
        MeasuringGateway gateway,
        HistoricalChunkPayloadBuilder payloadBuilder,
        V1LatestInBucketSampler sampler) {
      this.gateway = gateway;
      this.payloadBuilder = payloadBuilder;
      this.sampler = sampler;
    }

    private BenchmarkReport run(
        HistoricalTracksJobSubmission job,
        List<HistoricalChunkPlanEntry> chunks,
        HistoricalReadContext readContext) {
      gateway.resetMeasurements();
      Map<String, HistoricalSourceEvent> activeState = new LinkedHashMap<>();
      long renderNanos = 0L;
      long payloadNanos = 0L;
      long totalChunkNanos = 0L;
      long sampledPoints = 0L;
      int activeObjects = 0;
      Instant previousChunkEndExclusive = null;

      for (HistoricalChunkPlanEntry chunk : chunks) {
        long chunkStartNanos = System.nanoTime();

        if (previousChunkEndExclusive == null) {
          accumulateSnapshot(readContext, job, chunk, activeState);
        } else {
          accumulateDelta(readContext, job, chunk, previousChunkEndExclusive, activeState);
        }

        pruneInactive(activeState, chunk.to());
        activeObjects = activeState.size();

        var sampledChunk =
            sampler.sampleChunk(chunk, HistoricalResolution.MINUTE_1, activeState.values());
        sampledPoints += sampledChunk.pointCount();

        long renderStart = System.nanoTime();
        measureRenderPreparation(sampledChunk.points());
        renderNanos += System.nanoTime() - renderStart;

        long payloadStart = System.nanoTime();
        payloadBuilder.buildPayload(sampledChunk, payloadKeyFor(job, chunk.from()));
        payloadNanos += System.nanoTime() - payloadStart;

        totalChunkNanos += System.nanoTime() - chunkStartNanos;
        previousChunkEndExclusive = chunk.to();
      }

      return new BenchmarkReport(
          "",
          0L,
          0,
          nanosToMillis(gateway.snapshotNanos),
          nanosToMillis(gateway.deltaNanos),
          nanosToMillis(renderNanos),
          nanosToMillis(payloadNanos),
          nanosToMillis(totalChunkNanos) / Math.max(1, chunks.size()),
          nanosToMillis(totalChunkNanos),
          sampledPoints,
          activeObjects);
    }

    private void accumulateSnapshot(
        HistoricalReadContext readContext,
        HistoricalTracksJobSubmission job,
        HistoricalChunkPlanEntry chunk,
        Map<String, HistoricalSourceEvent> activeState) {
      HistoricalReadCursor cursor = null;
      HistoricalReadPage page;
      do {
        page = gateway.readActiveSnapshot(readContext, job, chunk, cursor, PAGE_SIZE);
        for (HistoricalSourceEvent event : page.rows()) {
          activeState.put(event.objectId(), event);
        }
        cursor = page.nextCursor();
      } while (page.hasMore());
    }

    private void accumulateDelta(
        HistoricalReadContext readContext,
        HistoricalTracksJobSubmission job,
        HistoricalChunkPlanEntry chunk,
        Instant previousChunkEndExclusive,
        Map<String, HistoricalSourceEvent> activeState) {
      HistoricalReadCursor cursor = null;
      HistoricalReadPage page;
      do {
        page =
            gateway.readDelta(
                readContext, job, chunk, previousChunkEndExclusive, cursor, PAGE_SIZE);
        for (HistoricalSourceEvent event : page.rows()) {
          activeState.put(event.objectId(), event);
        }
        cursor = page.nextCursor();
      } while (page.hasMore());
    }

    private void measureRenderPreparation(List<HistoricalSampledPlaybackPoint> points) {
      for (HistoricalSampledPlaybackPoint point : points) {
        try {
          Feature feature = objectMapper.readValue(point.payloadJson(), Feature.class);
          ElementMessage elementMessage = featureMapper.toMessage(feature);
          if (elementMessage == null) {
            throw new IllegalStateException("FeatureMapper returned null for " + point.eventId());
          }
        } catch (Exception e) {
          throw new IllegalStateException("Failed to render benchmark point " + point.eventId(), e);
        }
      }
    }

    private void pruneInactive(
        Map<String, HistoricalSourceEvent> activeState, Instant chunkEndExclusive) {
      activeState
          .values()
          .removeIf(
              event -> event.staleAt() != null && !event.staleAt().isAfter(chunkEndExclusive));
    }
  }

  private static final class MeasuringGateway {
    private final HistoricalPostGisChunkReadGateway delegate;
    private long snapshotNanos;
    private long deltaNanos;

    private MeasuringGateway(HistoricalPostGisChunkReadGateway delegate) {
      this.delegate = delegate;
    }

    private void resetMeasurements() {
      snapshotNanos = 0L;
      deltaNanos = 0L;
    }

    private HistoricalReadPage readActiveSnapshot(
        HistoricalReadContext readContext,
        HistoricalTracksJobSubmission job,
        HistoricalChunkPlanEntry chunk,
        HistoricalReadCursor cursor,
        int pageSize) {
      long start = System.nanoTime();
      try {
        return delegate.readActiveSnapshot(
            readContext, HistoricalExecutionContext.from(job), chunk, cursor, pageSize);
      } finally {
        snapshotNanos += System.nanoTime() - start;
      }
    }

    private HistoricalReadPage readDelta(
        HistoricalReadContext readContext,
        HistoricalTracksJobSubmission job,
        HistoricalChunkPlanEntry chunk,
        Instant previousChunkEndExclusive,
        HistoricalReadCursor cursor,
        int pageSize) {
      long start = System.nanoTime();
      try {
        return delegate.readDelta(
            readContext,
            HistoricalExecutionContext.from(job),
            chunk,
            previousChunkEndExclusive,
            cursor,
            pageSize);
      } finally {
        deltaNanos += System.nanoTime() - start;
      }
    }
  }

  private static long nanosToMillis(long nanos) {
    return nanos / 1_000_000L;
  }

  private String payloadKeyFor(HistoricalTracksJobSubmission job, Instant chunkFrom) {
    var subject = job.subjects().getFirst();
    var metadata =
        new HistoricalSubjectExecutionMeta(
            UUID.randomUUID(),
            job.jobId(),
            job.requestId(),
            job.issuer(),
            job.requestedBy(),
            job.requestedAt(),
            job.requestedFrom(),
            job.requestedTo(),
            job.effectiveFrom(),
            job.effectiveTo(),
            job.resolution(),
            job.plannedChunks(),
            job.filters(),
            subject.subjectType(),
            subject.subjectId(),
            new HistoricalChunkWindowPlan(
                job.effectiveFrom(),
                job.effectiveTo(),
                job.plannedChunks(),
                job.resolution(),
                List.of()));
    return new HistoricalCacheIdentity(objectMapper).payloadKey(metadata, chunkFrom);
  }

  private record RowSeed(
      UUID id,
      UUID sourceId,
      String objectId,
      Instant eventTime,
      Instant staleAt,
      int objectIndex,
      int chunkIndex) {}

  private record BenchmarkReport(
      String scenarioName,
      long datasetRows,
      int uniqueObjects,
      long snapshotMillis,
      long deltaMillis,
      long renderMillis,
      long payloadMillis,
      long averageChunkMillis,
      long maxChunkMillis,
      long sampledPoints,
      int activeObjects) {

    private BenchmarkReport withDatasetShape(DatasetShape datasetShape) {
      return new BenchmarkReport(
          scenarioName,
          datasetShape.datasetRows(),
          datasetShape.uniqueObjects(),
          snapshotMillis,
          deltaMillis,
          renderMillis,
          payloadMillis,
          averageChunkMillis,
          maxChunkMillis,
          sampledPoints,
          activeObjects);
    }

    private BenchmarkReport withScenarioName(String updatedScenarioName) {
      return new BenchmarkReport(
          updatedScenarioName,
          datasetRows,
          uniqueObjects,
          snapshotMillis,
          deltaMillis,
          renderMillis,
          payloadMillis,
          averageChunkMillis,
          maxChunkMillis,
          sampledPoints,
          activeObjects);
    }
  }

  private record LiveReadReport(String readPath, long rows, long pages, long nanos) {}

  private record LiveChunkReport(
      int chunkIndex,
      Instant from,
      Instant to,
      String readPath,
      long rows,
      long pages,
      int activeObjects,
      long sampledPoints,
      long readMillis,
      long sampleMillis,
      long renderMillis,
      long payloadMillis,
      long totalMillis) {}

  private record LiveBenchmarkReport(
      int chunks,
      long snapshotRows,
      long deltaRows,
      long snapshotPages,
      long deltaPages,
      long readMillis,
      long sampleMillis,
      long renderMillis,
      long payloadMillis,
      long totalMillis,
      long maxChunkMillis,
      List<LiveChunkReport> chunkReports) {}

  private record StateDeltaValidationReport(
      int activeObjectCount,
      int updatedObjectsPerChunk,
      int measuredChunks,
      List<Long> readMillisByChunk,
      List<Long> rowsByChunk,
      long p95Millis) {}

  private record DatasetShape(long datasetRows, int uniqueObjects) {}
}
