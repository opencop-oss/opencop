// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class HistoricalPostGisChunkReadGatewayIT {

  private static final java.util.Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void activeSnapshotTimedQuery_whenPostgresCancelsStatement_translatesToTimeoutFailure()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = new NamedParameterJdbcTemplate(postgres.getPostgresDatabase());

      var timeout =
          assertThrows(
              QueryTimeoutException.class,
              () ->
                  TimedNamedParameterJdbcQuery.query(
                      jdbc, "SELECT pg_sleep(3)", Map.of(), (_, _) -> null, 1));

      assertEquals(
          HistoricalJobStage.EXECUTION_FAILED_TIMEOUT,
          new HistoricalExecutionFailureClassifier().classify(timeout));
    }
  }

  @Test
  void readActiveSnapshot_executesAgainstRealPostgresAndReturnsLatestActiveWinners()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440200");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
          sourceId,
          "obj-1",
          Instant.parse("2026-04-06T10:00:30Z"),
          Instant.parse("2026-04-06T10:05:00Z"),
          "winner-old");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440202"),
          sourceId,
          "obj-1",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T10:06:00Z"),
          "winner-new");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
          sourceId,
          "obj-2",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "other-object");

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var page =
          gateway.readActiveSnapshot(
              HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10);

      assertEquals(2, page.rows().size());
      assertFalse(page.hasMore());
      assertEquals("obj-2", page.rows().get(0).objectId());
      assertEquals("obj-1", page.rows().get(1).objectId());
      assertEquals(
          UUID.fromString("550e8400-e29b-41d4-a716-446655440202"), page.rows().get(1).eventId());
      assertTrue(page.rows().get(1).payloadJson().contains("winner-new"));
    }
  }

  @Test
  void readActiveSnapshot_withCursor_executesJoinedQueryWithoutAmbiguity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440210");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440211"),
          sourceId,
          "obj-1",
          Instant.parse("2026-04-06T10:02:01Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "first");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440212"),
          sourceId,
          "obj-2",
          Instant.parse("2026-04-06T10:02:02Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "second");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440213"),
          sourceId,
          "obj-3",
          Instant.parse("2026-04-06T10:02:03Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "third");

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var firstPage =
          gateway.readActiveSnapshot(
              HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 2);

      assertTrue(firstPage.hasMore());
      assertNotNull(firstPage.nextCursor());

      HistoricalReadCursor cursor = firstPage.nextCursor();
      var secondPage =
          gateway.readActiveSnapshot(
              HistoricalExecutionContext.from(job(sourceId)), chunk(), cursor, 2);

      assertEquals(1, secondPage.rows().size());
      assertFalse(secondPage.hasMore());
      assertEquals("obj-3", secondPage.rows().get(0).objectId());
    }
  }

  @Test
  void readDelta_executesAgainstRealPostgresAndReturnsOnlyRowsInsideDeltaWindow() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440220");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440221"),
          sourceId,
          "obj-before-window",
          Instant.parse("2026-04-06T10:00:59Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "before-window");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440222"),
          sourceId,
          "obj-in-window-a",
          Instant.parse("2026-04-06T10:01:10Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "in-window-a");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440223"),
          sourceId,
          "obj-in-window-b",
          Instant.parse("2026-04-06T10:01:20Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "in-window-b");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440224"),
          sourceId,
          "obj-after-window",
          Instant.parse("2026-04-06T10:02:00Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "after-window");

      var gateway = gateway(dataSource, sourceId);
      var page =
          gateway.readDelta(
              HistoricalExecutionContext.from(job(sourceId)),
              nextChunk(),
              Instant.parse("2026-04-06T10:01:00Z"),
              null,
              10);

      assertEquals(2, page.rows().size());
      assertFalse(page.hasMore());
      assertEquals(
          List.of("obj-in-window-a", "obj-in-window-b"),
          page.rows().stream().map(row -> row.objectId()).toList());
    }
  }

  @Test
  void readDelta_withCursor_executesPaginationAgainstRealPostgres() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440230");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440231"),
          sourceId,
          "obj-a",
          Instant.parse("2026-04-06T10:01:01Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-a");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440232"),
          sourceId,
          "obj-b",
          Instant.parse("2026-04-06T10:01:02Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-b");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440233"),
          sourceId,
          "obj-c",
          Instant.parse("2026-04-06T10:01:03Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-c");

      var gateway = gateway(dataSource, sourceId);
      var firstPage =
          gateway.readDelta(
              HistoricalExecutionContext.from(job(sourceId)),
              nextChunk(),
              Instant.parse("2026-04-06T10:01:00Z"),
              null,
              2);

      assertTrue(firstPage.hasMore());
      assertNotNull(firstPage.nextCursor());
      assertEquals(
          List.of("obj-a", "obj-b"), firstPage.rows().stream().map(row -> row.objectId()).toList());

      var secondPage =
          gateway.readDelta(
              HistoricalExecutionContext.from(job(sourceId)),
              nextChunk(),
              Instant.parse("2026-04-06T10:01:00Z"),
              firstPage.nextCursor(),
              2);

      assertFalse(secondPage.hasMore());
      assertEquals(1, secondPage.rows().size());
      assertEquals("obj-c", secondPage.rows().getFirst().objectId());
    }
  }

  @Test
  void readDelta_streamAndPagedReadsReturnSameObjectAndEventOrderForSameContextWindow()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402b0");
      var row1 = UUID.fromString("550e8400-e29b-41d4-a716-4466554402b1");
      var row2 = UUID.fromString("550e8400-e29b-41d4-a716-4466554402b2");
      var row3 = UUID.fromString("550e8400-e29b-41d4-a716-4466554402b3");
      var row4 = UUID.fromString("550e8400-e29b-41d4-a716-4466554402b4");

      insertFeature(
          jdbc,
          row1,
          sourceId,
          "obj-a",
          Instant.parse("2026-04-06T10:02:05Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-a-1");
      insertFeature(
          jdbc,
          row2,
          sourceId,
          "obj-b",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-b-1");
      insertFeature(
          jdbc,
          row3,
          sourceId,
          "obj-c",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-c-1");
      insertFeature(
          jdbc,
          row4,
          sourceId,
          "obj-a",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-a-2");

      var gateway = gateway(dataSource, sourceId);
      var testJob = job(sourceId);
      var testContext = gateway.buildContext(HistoricalExecutionContext.from(testJob));
      var chunkToRead = chunk();

      var pagedRows = new ArrayList<HistoricalSourceEvent>();
      HistoricalReadCursor cursor = null;
      while (true) {
        var page =
            gateway.readDelta(
                testContext,
                HistoricalExecutionContext.from(testJob),
                chunkToRead,
                chunkToRead.from(),
                cursor,
                2);
        pagedRows.addAll(page.rows());
        if (!page.hasMore()) {
          break;
        }
        cursor = page.nextCursor();
      }

      var streamedRows = new ArrayList<HistoricalSourceEvent>();
      gateway.streamDelta(
          testContext,
          HistoricalExecutionContext.from(testJob),
          chunkToRead,
          chunkToRead.from(),
          2,
          streamedRows::add);

      assertEquals(
          pagedRows.stream().map(row -> row.objectId()).toList(),
          streamedRows.stream().map(row -> row.objectId()).toList());
      assertEquals(
          pagedRows.stream().map(row -> row.eventId()).toList(),
          streamedRows.stream().map(row -> row.eventId()).toList());
    }
  }

  @Test
  void readStateDeltaReturnsSameFinalStateAsFullDeltaReplayOnStaticEdgeCaseDataset()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655441100");
      UUID ignoredSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655441101");
      UUID objAOld = UUID.fromString("550e8400-e29b-41d4-a716-446655441110");
      UUID objANew = UUID.fromString("550e8400-e29b-41d4-a716-446655441111");
      UUID objBOnly = UUID.fromString("550e8400-e29b-41d4-a716-446655441120");
      UUID objCTieOld = UUID.fromString("550e8400-e29b-41d4-a716-446655441130");
      UUID objCTieWinner = UUID.fromString("550e8400-e29b-41d4-a716-446655441131");
      UUID objDStale = UUID.fromString("550e8400-e29b-41d4-a716-446655441140");
      UUID objEAfter = UUID.fromString("550e8400-e29b-41d4-a716-446655441150");
      UUID objFPlaybackTieOld = UUID.fromString("550e8400-e29b-41d4-a716-446655441170");
      UUID objFPlaybackTieWinner = UUID.fromString("550e8400-e29b-41d4-a716-446655441171");
      UUID objGIdTieOld = UUID.fromString("550e8400-e29b-41d4-a716-446655441180");
      UUID objGIdTieWinner = UUID.fromString("550e8400-e29b-41d4-a716-446655441181");

      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441109"),
          sourceId,
          "obj-a-repeat",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-a-before-delta");
      insertFeature(
          jdbc,
          objAOld,
          sourceId,
          "obj-a-repeat",
          Instant.parse("2026-04-06T10:02:05Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-a-old-delta");
      insertFeature(
          jdbc,
          objANew,
          sourceId,
          "obj-a-repeat",
          Instant.parse("2026-04-06T10:02:40Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-a-new-delta");
      insertFeature(
          jdbc,
          objBOnly,
          sourceId,
          "obj-b-single",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-b-delta");
      insertFeatureRow(
          jdbc,
          objCTieOld,
          sourceId,
          "obj-c-created-tie",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-c-created-tie-old");
      insertFeatureRow(
          jdbc,
          objCTieWinner,
          sourceId,
          "obj-c-created-tie",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:02:21Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-c-created-tie-winner");
      insertFeature(
          jdbc,
          objDStale,
          sourceId,
          "obj-d-stale-in-delta",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T10:02:45Z"),
          "obj-d-stale-in-delta");
      insertFeatureRow(
          jdbc,
          objFPlaybackTieOld,
          sourceId,
          "obj-f-playback-tie",
          Instant.parse("2026-04-06T10:02:15Z"),
          Instant.parse("2026-04-06T10:02:55Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-f-playback-tie-old");
      insertFeatureRow(
          jdbc,
          objFPlaybackTieWinner,
          sourceId,
          "obj-f-playback-tie",
          Instant.parse("2026-04-06T10:02:35Z"),
          Instant.parse("2026-04-06T10:02:16Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-f-playback-tie-winner");
      insertFeatureRow(
          jdbc,
          objGIdTieOld,
          sourceId,
          "obj-g-id-tie",
          Instant.parse("2026-04-06T10:02:25Z"),
          Instant.parse("2026-04-06T10:02:25Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-g-id-tie-old");
      insertFeatureRow(
          jdbc,
          objGIdTieWinner,
          sourceId,
          "obj-g-id-tie",
          Instant.parse("2026-04-06T10:02:25Z"),
          Instant.parse("2026-04-06T10:02:25Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-g-id-tie-winner");
      insertFeature(
          jdbc,
          objEAfter,
          sourceId,
          "obj-e-after-window",
          Instant.parse("2026-04-06T10:03:00Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "obj-e-after-window");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441160"),
          ignoredSourceId,
          "obj-a-repeat",
          Instant.parse("2026-04-06T10:02:50Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "other-source-ignored");

      seedHelperFromFeatures(jdbc, sourceId);
      seedHelperFromFeatures(jdbc, ignoredSourceId);

      var gateway = gateway(dataSource, sourceId);
      var executionContext =
          HistoricalExecutionContext.from(job(sourceId, HistoricalResolution.DAY_1));
      var readContext = new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      var previousChunkEnd = Instant.parse("2026-04-06T10:02:00Z");

      var fullDeltaRows = new ArrayList<HistoricalSourceEvent>();
      gateway.streamDelta(
          readContext, executionContext, chunk(), previousChunkEnd, 2, fullDeltaRows::add);
      var fullState = finalStateByObject(fullDeltaRows);

      var compressedRows = new ArrayList<HistoricalSourceEvent>();
      gateway.streamLatestStateDelta(
          readContext, executionContext, chunk(), previousChunkEnd, 2, compressedRows::add);
      var compressedState = finalStateByObject(compressedRows);

      assertEquals(10, fullDeltaRows.size(), "Full delta path should keep every event row");
      assertEquals(
          List.of(
              "obj-b-single",
              "obj-c-created-tie",
              "obj-g-id-tie",
              "obj-d-stale-in-delta",
              "obj-f-playback-tie",
              "obj-a-repeat"),
          compressedRows.stream().map(HistoricalSourceEvent::objectId).toList());
      assertEquals(
          List.of(
              objBOnly, objCTieWinner, objGIdTieWinner, objDStale, objFPlaybackTieWinner, objANew),
          compressedRows.stream().map(HistoricalSourceEvent::eventId).toList());
      assertStateEventIdsEqual(fullState, compressedState);
    }
  }

  @Test
  void readStateDeltaReturnsOneWinnerPerSourceWhenSourcesShareObjectId() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceA = UUID.fromString("550e8400-e29b-41d4-a716-446655441200");
      UUID sourceB = UUID.fromString("550e8400-e29b-41d4-a716-446655441201");
      UUID sourceAOld = UUID.fromString("550e8400-e29b-41d4-a716-446655441202");
      UUID sourceAWinner = UUID.fromString("550e8400-e29b-41d4-a716-446655441203");
      UUID sourceBOld = UUID.fromString("550e8400-e29b-41d4-a716-446655441204");
      UUID sourceBWinner = UUID.fromString("550e8400-e29b-41d4-a716-446655441205");

      insertFeature(
          jdbc,
          sourceAOld,
          sourceA,
          "shared-object",
          Instant.parse("2026-04-06T10:02:05Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "source-a-old");
      insertFeature(
          jdbc,
          sourceAWinner,
          sourceA,
          "shared-object",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "source-a-winner");
      insertFeature(
          jdbc,
          sourceBOld,
          sourceB,
          "shared-object",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "source-b-old");
      insertFeature(
          jdbc,
          sourceBWinner,
          sourceB,
          "shared-object",
          Instant.parse("2026-04-06T10:02:40Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "source-b-winner");

      var gateway = gateway(dataSource, sourceA);
      var executionContext =
          HistoricalExecutionContext.from(
              layerJobWithSources(UUID.randomUUID(), List.of(sourceA, sourceB)));
      var readContext = new HistoricalReadContext(List.of(sourceA, sourceB), null, false);
      var previousChunkEnd = Instant.parse("2026-04-06T10:02:00Z");

      var compressedRows = new ArrayList<HistoricalSourceEvent>();
      gateway.streamLatestStateDelta(
          readContext, executionContext, chunk(), previousChunkEnd, 2, compressedRows::add);

      assertEquals(
          List.of(sourceA, sourceB),
          compressedRows.stream().map(HistoricalSourceEvent::sourceId).toList());
      assertEquals(
          List.of("shared-object", "shared-object"),
          compressedRows.stream().map(HistoricalSourceEvent::objectId).toList());
      assertEquals(
          List.of(sourceAWinner, sourceBWinner),
          compressedRows.stream().map(HistoricalSourceEvent::eventId).toList());
      assertTrue(compressedRows.get(0).payloadJson().contains("source-a-winner"));
      assertTrue(compressedRows.get(1).payloadJson().contains("source-b-winner"));
    }
  }

  @Test
  void readActiveSnapshot_streamAndPagedReadsMatchOrderForSingleSource() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402c0");
      UUID stableOneId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402c1");
      UUID stableTwoOldId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402c2");
      UUID stableTwoNewId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402c3");
      UUID residualOneId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402c4");
      UUID residualTwoId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402c5");

      insertFeature(
          jdbc,
          stableOneId,
          sourceId,
          "obj-stable-a",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "stable-a");
      insertFeature(
          jdbc,
          stableTwoOldId,
          sourceId,
          "obj-stable-b",
          Instant.parse("2026-04-06T10:01:10Z"),
          Instant.parse("2026-04-06T10:02:20Z"),
          "stable-b-old");
      insertFeature(
          jdbc,
          stableTwoNewId,
          sourceId,
          "obj-stable-b",
          Instant.parse("2026-04-06T10:01:50Z"),
          Instant.parse("2026-04-06T10:02:30Z"),
          "stable-b-new");
      insertFeature(
          jdbc,
          residualOneId,
          sourceId,
          "obj-residual-a",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:03:30Z"),
          "residual-a");
      insertFeature(
          jdbc,
          residualTwoId,
          sourceId,
          "obj-residual-b",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:03:30Z"),
          "residual-b");

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var testJob = job(sourceId);
      var testContext = gateway.buildContext(HistoricalExecutionContext.from(testJob));
      var chunkToRead = chunk();

      var pagedRows = new ArrayList<HistoricalSourceEvent>();
      HistoricalReadCursor cursor = null;
      while (true) {
        var page =
            gateway.readActiveSnapshot(
                testContext, HistoricalExecutionContext.from(testJob), chunkToRead, cursor, 2);
        pagedRows.addAll(page.rows());
        if (!page.hasMore()) {
          break;
        }
        cursor = page.nextCursor();
      }

      var streamedRows = new ArrayList<HistoricalSourceEvent>();
      gateway.streamActiveSnapshot(
          testContext,
          HistoricalExecutionContext.from(testJob),
          chunkToRead,
          null,
          2,
          streamedRows::add);
      assertEquals(
          pagedRows.stream().map(row -> row.objectId()).toList(),
          streamedRows.stream().map(row -> row.objectId()).toList());
      assertEquals(
          pagedRows.stream().map(row -> row.eventId()).toList(),
          streamedRows.stream().map(row -> row.eventId()).toList());
    }
  }

  @Test
  void readActiveSnapshot_withHelperTableEnabled_returnsPersistedLatestActiveRow()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440240");
      UUID eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440241");
      insertFeature(
          jdbc,
          eventId,
          sourceId,
          "obj-helper",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "helper-winner");
      jdbc.update(
          """
          INSERT INTO geo.feature_latest_active_helper
            (source, object_id, event_id, playback_at, first_emission_at, created_at, stale_at)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          sourceId,
          "obj-helper",
          eventId,
          Timestamp.from(Instant.parse("2026-04-06T10:02:10Z")),
          Timestamp.from(Instant.parse("2026-04-06T10:02:10Z")),
          Timestamp.from(Instant.parse("2026-04-06T10:02:10Z")),
          Timestamp.from(Instant.parse("2026-04-06T10:10:00Z")));

      var gateway = gateway(dataSource, sourceId);
      var context = new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);

      var page =
          gateway.readActiveSnapshot(
              context, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10);

      assertEquals(1, page.rows().size());
      assertEquals("obj-helper", page.rows().getFirst().objectId());
      assertTrue(page.rows().getFirst().payloadJson().contains("helper-winner"));
    }
  }

  @Test
  void readActiveSnapshot_lateHelperCandidatesUseLatestBeforeToStaleOverlap() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402d0");
      UUID stableId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402d1");
      UUID residualWinnerId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402d2");
      UUID residualLatestId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402d3");
      insertFeature(
          jdbc,
          stableId,
          sourceId,
          "stable-helper",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "stable-helper");
      insertFeature(
          jdbc,
          residualWinnerId,
          sourceId,
          "residual-backed",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "residual-backed-winner");
      insertFeature(
          jdbc,
          residualLatestId,
          sourceId,
          "residual-backed",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "residual-backed-latest");
      jdbc.update(
          """
          INSERT INTO geo.features (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
          SELECT
            gen_random_uuid(),
            'TRACK',
            ?,
            'no-winner-' || gs.obj_idx,
            CASE WHEN gs.rev_idx = 0
              THEN '2026-04-06T10:03:30Z'::timestamptz
              ELSE '2026-04-06T10:01:30Z'::timestamptz - (gs.rev_idx * INTERVAL '1 second')
            END,
            CASE WHEN gs.rev_idx = 0
              THEN '2026-04-06T10:03:30Z'::timestamptz
              ELSE '2026-04-06T10:01:30Z'::timestamptz - (gs.rev_idx * INTERVAL '1 second')
            END,
            CASE WHEN gs.rev_idx = 0
              THEN '2026-04-06T11:00:00Z'::timestamptz
              ELSE '2026-04-06T10:02:00Z'::timestamptz
            END,
            '{"type":"Point","coordinates":[-73.5,45.5]}',
            ('{"type":"Feature","properties":{"marker":"no-winner-' || gs.obj_idx || '-' || gs.rev_idx || '"}}')::jsonb
          FROM (
            SELECT obj_idx, rev_idx
            FROM generate_series(1, 30) obj_idx
            CROSS JOIN generate_series(0, 2) rev_idx
          ) gs
          """,
          sourceId);

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var context = new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);

      var page =
          gateway.readActiveSnapshot(
              context, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10);

      assertEquals(
          List.of("stable-helper", "residual-backed"),
          page.rows().stream().map(HistoricalSourceEvent::objectId).toList());
      assertEquals(
          List.of(stableId, residualLatestId),
          page.rows().stream().map(HistoricalSourceEvent::eventId).toList());
    }
  }

  @Test
  void readActiveSnapshot_withCheckpointEnabled_replaysDeltaRowsAfterCheckpoint() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440250");
      UUID checkpointEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440251");
      UUID deltaEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440252");
      insertFeature(
          jdbc,
          checkpointEventId,
          sourceId,
          "obj-checkpoint",
          Instant.parse("2026-04-06T10:01:00Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "checkpoint");
      insertFeature(
          jdbc,
          deltaEventId,
          sourceId,
          "obj-checkpoint",
          Instant.parse("2026-04-06T10:02:15Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "delta-newer");
      jdbc.update(
          """
          INSERT INTO geo.feature_history_checkpoint
            (source, object_id, resolution, checkpoint_at, event_id, playback_at, created_at, stale_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          sourceId,
          "obj-checkpoint",
          HistoricalResolution.MINUTE_1.name(),
          Timestamp.from(Instant.parse("2026-04-06T10:02:00Z")),
          checkpointEventId,
          Timestamp.from(Instant.parse("2026-04-06T10:01:00Z")),
          Timestamp.from(Instant.parse("2026-04-06T10:01:00Z")),
          Timestamp.from(Instant.parse("2026-04-06T10:10:00Z")));

      var gateway = gateway(dataSource, sourceId);
      var context =
          new HistoricalReadContext(
              List.of(sourceId), sourceId, false, HistoricalResolution.MINUTE_1, true);

      var page =
          gateway.readActiveSnapshot(
              context, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10);

      assertEquals(1, page.rows().size());
      assertEquals(deltaEventId, page.rows().getFirst().eventId());
      assertTrue(page.rows().getFirst().payloadJson().contains("delta-newer"));
    }
  }

  @Test
  void readActiveSnapshot_forLayerJob_readsDirectlyByLayerIdWithoutSourceExpansion()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID layerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440260");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440261"),
          layerId,
          "obj-layer",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "layer-row");

      seedHelperFromFeatures(jdbc, layerId);

      var gateway = gateway(dataSource, layerId);
      var page =
          gateway.readActiveSnapshot(
              HistoricalExecutionContext.from(layerJob(layerId)), chunk(), null, 10);

      assertEquals(1, page.rows().size());
      assertEquals(layerId, page.rows().getFirst().sourceId());
      assertEquals("obj-layer", page.rows().getFirst().objectId());
    }
  }

  @Test
  void fastPathRowsMatchWinnersSqlOnBaselineDataset() throws Exception {
    // Seed a dataset shaped like a typical recent-playback workload: many objects, each with
    // a few historical emissions. Verify that the single-source helper fast path selects the
    // same object winners (by objectId and eventId) as the legacy full-scan WINNERS_SQL path.
    // Any divergence here means the fast path has a bug in its stable-winner or residual
    // classification logic.
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440270");
      // 60 stable-winner objects: all emissions before chunk.from, so latest < chunk.from.
      // The helper row is the definitive DISTINCT ON winner — no newer emission in [from, to).
      jdbc.execute(
          """
          INSERT INTO geo.features (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
          SELECT
            gen_random_uuid(),
            'TRACK',
            '550e8400-e29b-41d4-a716-446655440270'::uuid,
            'stable-' || gs.obj_idx,
            '2026-04-06T10:02:00Z'::timestamptz - (gs.rev_idx * INTERVAL '90 seconds'),
            '2026-04-06T10:02:00Z'::timestamptz - (gs.rev_idx * INTERVAL '90 seconds'),
            '2026-04-06T11:00:00Z'::timestamptz,
            '{"type":"Point","coordinates":[-73.5,45.5]}',
            ('{"type":"Feature","properties":{"marker":"stable-' || gs.obj_idx || '-' || gs.rev_idx || '"}}')::jsonb
          FROM (
            SELECT obj_idx, rev_idx
            FROM generate_series(1, 60) obj_idx
            CROSS JOIN generate_series(1, 3) rev_idx
          ) gs
          """);
      // 40 residual objects: latest emission inside [chunk.from, chunk.to), so the helper
      // alone cannot determine the winner — the full DISTINCT ON scan is required.
      jdbc.execute(
          """
          INSERT INTO geo.features (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
          SELECT
            gen_random_uuid(),
            'TRACK',
            '550e8400-e29b-41d4-a716-446655440270'::uuid,
            'residual-' || gs.obj_idx,
            CASE WHEN gs.rev_idx = 1
              THEN '2026-04-06T10:02:15Z'::timestamptz + (gs.obj_idx * INTERVAL '0.1 seconds')
              ELSE '2026-04-06T10:02:00Z'::timestamptz - (gs.rev_idx * INTERVAL '90 seconds')
            END,
            CASE WHEN gs.rev_idx = 1
              THEN '2026-04-06T10:02:15Z'::timestamptz + (gs.obj_idx * INTERVAL '0.1 seconds')
              ELSE '2026-04-06T10:02:00Z'::timestamptz - (gs.rev_idx * INTERVAL '90 seconds')
            END,
            '2026-04-06T11:00:00Z'::timestamptz,
            '{"type":"Point","coordinates":[-73.5,45.5]}',
            ('{"type":"Feature","properties":{"marker":"residual-' || gs.obj_idx || '-' || gs.rev_idx || '"}}')::jsonb
          FROM (
            SELECT obj_idx, rev_idx
            FROM generate_series(1, 40) obj_idx
            CROSS JOIN generate_series(1, 3) rev_idx
          ) gs
          """);

      seedHelperFromFeatures(jdbc, sourceId);

      // Fast path: single-source context, no cursor.
      var gateway = gateway(dataSource, sourceId);
      var fastPathContext =
          new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      var fastPathPage =
          gateway.readActiveSnapshot(
              fastPathContext,
              HistoricalExecutionContext.from(job(sourceId)),
              chunk(),
              null,
              10_000);

      // Legacy WINNERS_SQL path: pass singleSourceId=null so the dispatcher routes to WINNERS_SQL.
      var legacyContext = new HistoricalReadContext(List.of(sourceId), null, false, null, false);
      var legacyPage =
          gateway.readActiveSnapshot(
              legacyContext, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10_000);

      // Both paths must select the same set of objects and the same winning event per object.
      var fastObjectIds = fastPathPage.rows().stream().map(r -> r.objectId()).sorted().toList();
      var legacyObjectIds = legacyPage.rows().stream().map(r -> r.objectId()).sorted().toList();
      assertEquals(
          legacyObjectIds, fastObjectIds, "Fast path and WINNERS_SQL must select the same objects");

      var fastEventIds =
          fastPathPage.rows().stream()
              .sorted(java.util.Comparator.comparing(r -> r.objectId()))
              .map(r -> r.eventId())
              .toList();
      var legacyEventIds =
          legacyPage.rows().stream()
              .sorted(java.util.Comparator.comparing(r -> r.objectId()))
              .map(r -> r.eventId())
              .toList();
      assertEquals(
          legacyEventIds,
          fastEventIds,
          "Fast path and WINNERS_SQL must select the same winning event per object");
    }
  }

  @Test
  void fastPathRowsMatchWinnersSqlOnSkewDataset() throws Exception {
    // Seed a dataset shaped like a high-history (skewed) workload: many objects each with many
    // historical emissions spread over a long window before the chunk. Most objects are stable
    // winners because their latest emission is well before chunk.from(). Verify that the fast
    // path selects the same winners as the WINNERS_SQL path.
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440280");
      // 80 stable-winner objects, each with 10 historical rows spread over the past 2 hours.
      // Latest emission is always at chunk.from - 5 minutes, so all are stable winners.
      jdbc.execute(
          """
          INSERT INTO geo.features (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
          SELECT
            gen_random_uuid(),
            'TRACK',
            '550e8400-e29b-41d4-a716-446655440280'::uuid,
            'skew-stable-' || gs.obj_idx,
            '2026-04-06T09:57:00Z'::timestamptz - (gs.rev_idx * INTERVAL '12 minutes'),
            '2026-04-06T09:57:00Z'::timestamptz - (gs.rev_idx * INTERVAL '12 minutes'),
            '2026-04-06T12:00:00Z'::timestamptz,
            '{"type":"Point","coordinates":[-73.5,45.5]}',
            ('{"type":"Feature","properties":{"marker":"skew-stable-' || gs.obj_idx || '-' || gs.rev_idx || '"}}')::jsonb
          FROM (
            SELECT obj_idx, rev_idx
            FROM generate_series(1, 80) obj_idx
            CROSS JOIN generate_series(0, 9) rev_idx
          ) gs
          """);
      // 20 residual objects with latest emission inside [chunk.from, chunk.to).
      jdbc.execute(
          """
          INSERT INTO geo.features (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
          SELECT
            gen_random_uuid(),
            'TRACK',
            '550e8400-e29b-41d4-a716-446655440280'::uuid,
            'skew-residual-' || gs.obj_idx,
            CASE WHEN gs.rev_idx = 0
              THEN '2026-04-06T10:02:10Z'::timestamptz + (gs.obj_idx * INTERVAL '0.5 seconds')
              ELSE '2026-04-06T09:57:00Z'::timestamptz - (gs.rev_idx * INTERVAL '10 minutes')
            END,
            CASE WHEN gs.rev_idx = 0
              THEN '2026-04-06T10:02:10Z'::timestamptz + (gs.obj_idx * INTERVAL '0.5 seconds')
              ELSE '2026-04-06T09:57:00Z'::timestamptz - (gs.rev_idx * INTERVAL '10 minutes')
            END,
            '2026-04-06T12:00:00Z'::timestamptz,
            '{"type":"Point","coordinates":[-73.5,45.5]}',
            ('{"type":"Feature","properties":{"marker":"skew-residual-' || gs.obj_idx || '-' || gs.rev_idx || '"}}')::jsonb
          FROM (
            SELECT obj_idx, rev_idx
            FROM generate_series(1, 20) obj_idx
            CROSS JOIN generate_series(0, 9) rev_idx
          ) gs
          """);

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var fastPathContext =
          new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      var fastPathPage =
          gateway.readActiveSnapshot(
              fastPathContext,
              HistoricalExecutionContext.from(job(sourceId)),
              chunk(),
              null,
              10_000);

      var legacyContext = new HistoricalReadContext(List.of(sourceId), null, false, null, false);
      var legacyPage =
          gateway.readActiveSnapshot(
              legacyContext, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10_000);

      var fastObjectIds = fastPathPage.rows().stream().map(r -> r.objectId()).sorted().toList();
      var legacyObjectIds = legacyPage.rows().stream().map(r -> r.objectId()).sorted().toList();
      assertEquals(
          legacyObjectIds,
          fastObjectIds,
          "Fast path and WINNERS_SQL must select the same objects on the skew dataset");

      var fastEventIds =
          fastPathPage.rows().stream()
              .sorted(java.util.Comparator.comparing(r -> r.objectId()))
              .map(r -> r.eventId())
              .toList();
      var legacyEventIds =
          legacyPage.rows().stream()
              .sorted(java.util.Comparator.comparing(r -> r.objectId()))
              .map(r -> r.eventId())
              .toList();
      assertEquals(
          legacyEventIds,
          fastEventIds,
          "Fast path and WINNERS_SQL must select the same winning event per object on the skew dataset");
    }
  }

  @Test
  void fastPathReturnsMidChunkStaleObject() throws Exception {
    // An object that was alive at chunk.from() and went stale mid-chunk (stale_at is strictly
    // between chunk.from and chunk.to) must appear in the fast-path result. The interval-overlap
    // contract requires such objects to remain in the chunk payload so downstream processing
    // can observe the state transition; excluding them would silently drop tracked entities.
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440290");
      // Object emitted before chunk.from(), goes stale at chunk.from + 30s (inside the window).
      UUID midChunkStaleEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440291");
      insertFeature(
          jdbc,
          midChunkStaleEventId,
          sourceId,
          "obj-mid-stale",
          Instant.parse("2026-04-06T10:01:00Z"), // start_at before chunk.from
          Instant.parse("2026-04-06T10:02:30Z"), // stale_at inside [from, to): alive at from
          "mid-chunk-stale");

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var context = new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      var page =
          gateway.readActiveSnapshot(
              context, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10);

      assertEquals(1, page.rows().size(), "Object stale mid-chunk must appear in fast-path result");
      assertEquals("obj-mid-stale", page.rows().getFirst().objectId());
      assertEquals(midChunkStaleEventId, page.rows().getFirst().eventId());
    }
  }

  @Test
  void fastPathExcludesObjectStaleBeforeChunkFrom() throws Exception {
    // An object whose stale_at is at or before chunk.from() must be excluded from the fast-path
    // result. The interval-overlap predicate requires the entity to be alive at chunk.from();
    // an entity that went stale before or exactly at chunk.from() is already inactive and must
    // not appear in the snapshot. This mirrors the behaviour of HistoricalChunkExecutionCoordinator
    // which drops entities whose staleAt <= chunk.from() during chunk pruning.
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402a0");
      // Object stale at 10:01:59Z — strictly before chunk.from = 10:02:00Z. Must be excluded.
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-4466554402a1"),
          sourceId,
          "obj-stale-before-from",
          Instant.parse("2026-04-06T10:00:00Z"),
          Instant.parse("2026-04-06T10:01:59Z"), // stale before chunk.from
          "stale-before-from");
      // Control object: alive throughout the chunk window. Must appear in the result.
      UUID liveEventId = UUID.fromString("550e8400-e29b-41d4-a716-4466554402a2");
      insertFeature(
          jdbc,
          liveEventId,
          sourceId,
          "obj-live",
          Instant.parse("2026-04-06T10:01:00Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "live-object");

      seedHelperFromFeatures(jdbc, sourceId);

      var gateway = gateway(dataSource, sourceId);
      var context = new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      var page =
          gateway.readActiveSnapshot(
              context, HistoricalExecutionContext.from(job(sourceId)), chunk(), null, 10);

      assertEquals(
          1,
          page.rows().size(),
          "Only the live object must appear; stale-before-from must be excluded");
      assertEquals("obj-live", page.rows().getFirst().objectId());
      assertEquals(liveEventId, page.rows().getFirst().eventId());
    }
  }

  @Test
  void readChunkReturnsOneWinnerPerSourceObjectIdPair() throws Exception {
    // Two distinct sources share the same object_id. Under the corrected WINNERS_SQL, which
    // partitions winners by (source, object_id), each source must produce its own independent
    // winner row — so the result must contain exactly two rows, one per source.
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceA = UUID.fromString("550e8400-e29b-41d4-a716-446655440300");
      UUID sourceB = UUID.fromString("550e8400-e29b-41d4-a716-446655440301");
      UUID eventA = UUID.fromString("550e8400-e29b-41d4-a716-446655440302");
      UUID eventB = UUID.fromString("550e8400-e29b-41d4-a716-446655440303");

      // Both sources have a row for the same object_id inside the chunk window.
      insertFeature(
          jdbc,
          eventA,
          sourceA,
          "ABC-123",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "source-a-winner");
      insertFeature(
          jdbc,
          eventB,
          sourceB,
          "ABC-123",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "source-b-winner");

      var gateway = gatewayForSources(dataSource, List.of(sourceA, sourceB));
      var page =
          gateway.readActiveSnapshot(
              HistoricalExecutionContext.from(
                  layerJobWithSources(UUID.randomUUID(), List.of(sourceA, sourceB))),
              chunk(),
              null,
              10);

      assertEquals(2, page.rows().size(), "Expected one winner row per (source, object_id) pair");
      assertFalse(page.hasMore());

      var tuples =
          page.rows().stream()
              .map(row -> row.sourceId() + ":" + row.objectId())
              .collect(java.util.stream.Collectors.toSet());
      assertTrue(
          tuples.contains(sourceA + ":ABC-123"),
          "Result must contain a winner for sourceA / ABC-123");
      assertTrue(
          tuples.contains(sourceB + ":ABC-123"),
          "Result must contain a winner for sourceB / ABC-123");

      // Each row must be the latest emission within its source.
      var rowBySource =
          page.rows().stream()
              .collect(java.util.stream.Collectors.toMap(row -> row.sourceId(), row -> row));
      assertEquals(eventA, rowBySource.get(sourceA).eventId());
      assertEquals(eventB, rowBySource.get(sourceB).eventId());
    }
  }

  @Test
  void readChunkSingleSourceWithDuplicateObjectIdAcrossSourcesIgnoresOtherSource()
      throws Exception {
    // When the resolver is restricted to a single source, rows from any other source that shares
    // the same object_id must not appear in the result. This verifies that the single-source
    // helper fast path is unaffected by the multi-source WINNERS_SQL change.
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceA = UUID.fromString("550e8400-e29b-41d4-a716-446655440310");
      UUID sourceB = UUID.fromString("550e8400-e29b-41d4-a716-446655440311");
      UUID eventA = UUID.fromString("550e8400-e29b-41d4-a716-446655440312");

      insertFeature(
          jdbc,
          eventA,
          sourceA,
          "ABC-123",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "source-a-winner");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440313"),
          sourceB,
          "ABC-123",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          "source-b-ignored");

      // Seed the helper for sourceA only so the single-source fast path can classify rows.
      seedHelperFromFeatures(jdbc, sourceA);

      // Gateway resolves only sourceA; sourceB rows must be invisible.
      var gateway = gateway(dataSource, sourceA);
      var page =
          gateway.readActiveSnapshot(
              HistoricalExecutionContext.from(job(sourceA)), chunk(), null, 10);

      assertEquals(1, page.rows().size(), "Only the row from sourceA must be returned");
      assertFalse(page.hasMore());
      assertEquals(sourceA, page.rows().getFirst().sourceId());
      assertEquals("ABC-123", page.rows().getFirst().objectId());
      assertEquals(eventA, page.rows().getFirst().eventId());
    }
  }

  @Test
  void helperFastPathAndStreamingMatchLegacyWinnersOnStaticEdgeCaseDataset() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655441000");
      UUID ignoredSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");

      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441010"),
          sourceId,
          "stable",
          Instant.parse("2026-04-06T10:00:00Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "stable-old");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441011"),
          sourceId,
          "stable",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "stable-winner");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441020"),
          sourceId,
          "residual",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "residual-old");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441021"),
          sourceId,
          "residual",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "residual-winner");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441030"),
          sourceId,
          "future-latest",
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T10:01:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "future-latest-old");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441031"),
          sourceId,
          "future-latest",
          Instant.parse("2026-04-06T10:02:25Z"),
          Instant.parse("2026-04-06T10:02:25Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "future-latest-winner");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441032"),
          sourceId,
          "future-latest",
          Instant.parse("2026-04-06T10:10:00Z"),
          Instant.parse("2026-04-06T10:10:00Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "future-latest-after-to");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441040"),
          sourceId,
          "created-at-tie",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "created-at-tie-old");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441041"),
          sourceId,
          "created-at-tie",
          Instant.parse("2026-04-06T10:02:20Z"),
          Instant.parse("2026-04-06T10:02:21Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "created-at-tie-winner");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441050"),
          sourceId,
          "event-id-tie",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "event-id-tie-old");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441051"),
          sourceId,
          "event-id-tie",
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T10:02:30Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "event-id-tie-winner");
      insertFeatureRow(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441060"),
          sourceId,
          "created-at-only-order",
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T10:02:10Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "created-at-only-order-winner");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441070"),
          sourceId,
          "stale-mid-chunk",
          Instant.parse("2026-04-06T10:01:00Z"),
          Instant.parse("2026-04-06T10:02:30Z"),
          "stale-mid-chunk-winner");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441080"),
          sourceId,
          "stale-at-from",
          Instant.parse("2026-04-06T10:01:00Z"),
          Instant.parse("2026-04-06T10:02:00Z"),
          "stale-at-from-excluded");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655441090"),
          sourceId,
          "future-only",
          Instant.parse("2026-04-06T10:03:00Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "future-only-excluded");
      insertFeature(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-4466554410a0"),
          ignoredSourceId,
          "residual",
          Instant.parse("2026-04-06T10:02:50Z"),
          Instant.parse("2026-04-06T11:00:00Z"),
          "other-source-ignored");

      seedHelperFromFeatures(jdbc, sourceId);
      seedHelperFromFeatures(jdbc, ignoredSourceId);

      var gateway = gateway(dataSource, sourceId);
      var fastPathContext =
          new HistoricalReadContext(List.of(sourceId), sourceId, true, null, false);
      var legacyContext = new HistoricalReadContext(List.of(sourceId), null, false, null, false);
      var executionContext = HistoricalExecutionContext.from(job(sourceId));

      var legacyRows =
          gateway.readActiveSnapshot(legacyContext, executionContext, chunk(), null, 100).rows();
      var fastRows =
          gateway.readActiveSnapshot(fastPathContext, executionContext, chunk(), null, 100).rows();
      var pagedFastRows = new ArrayList<HistoricalSourceEvent>();
      HistoricalReadCursor cursor = null;
      do {
        var page =
            gateway.readActiveSnapshot(fastPathContext, executionContext, chunk(), cursor, 3);
        pagedFastRows.addAll(page.rows());
        cursor = page.nextCursor();
        if (!page.hasMore()) {
          break;
        }
      } while (true);
      var streamedRows = new ArrayList<HistoricalSourceEvent>();
      gateway.streamActiveSnapshot(
          fastPathContext, executionContext, chunk(), null, 2, streamedRows::add);

      assertRowsExactlyEqual(legacyRows, fastRows);
      assertRowsExactlyEqual(legacyRows, pagedFastRows);
      assertRowsExactlyEqual(legacyRows, streamedRows);
      assertEquals(
          List.of(
              "stale-mid-chunk",
              "stable",
              "created-at-only-order",
              "created-at-tie",
              "residual",
              "future-latest",
              "event-id-tie"),
          legacyRows.stream().map(HistoricalSourceEvent::objectId).toList());
      assertEquals(
          List.of(
              UUID.fromString("550e8400-e29b-41d4-a716-446655441070"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655441011"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655441060"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655441041"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655441021"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655441031"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655441051")),
          legacyRows.stream().map(HistoricalSourceEvent::eventId).toList());
    }
  }

  /**
   * Seeds geo.feature_latest_active_helper for a given source from the current rows in
   * geo.features. For each (source, object_id) pair, picks the globally latest emission as the
   * helper row and computes first_emission_at from the earliest emission. This mirrors the upsert
   * logic in PostGisService.HELPER_SQL so that integration tests that seed geo.features directly
   * can also exercise the helper-resident fast path.
   */
  private static void seedHelperFromFeatures(JdbcTemplate jdbc, UUID sourceId) {
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
  }

  private static HistoricalPostGisChunkReadGateway gateway(DataSource dataSource, UUID sourceId) {
    var observability = new HistoricalObservability(new SimpleMeterRegistry());
    return new HistoricalPostGisChunkReadGateway(
        new NamedParameterJdbcTemplate(dataSource),
        new DataSourceTransactionManager(dataSource),
        observability,
        new HistoricalExecutionProperties(),
        io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
  }

  private static HistoricalPostGisChunkReadGateway gatewayForSources(
      DataSource dataSource, List<UUID> sourceIds) {
    var observability = new HistoricalObservability(new SimpleMeterRegistry());
    return new HistoricalPostGisChunkReadGateway(
        new NamedParameterJdbcTemplate(dataSource),
        new DataSourceTransactionManager(dataSource),
        observability,
        new HistoricalExecutionProperties(),
        io.opentelemetry.api.GlobalOpenTelemetry.getTracer("test"));
  }

  private static HistoricalTracksJobSubmission layerJobWithSources(
      UUID layerId, List<UUID> sourceIds) {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        sourceIds.stream()
            .map(
                sourceId ->
                    new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.SOURCE, sourceId))
            .toList(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of());
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations(sdkMigrationLocation())
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static String sdkMigrationLocation() {
    return "filesystem:"
        + Path.of("..", "sdk", "src", "main", "resources", "db", "migration").toAbsolutePath();
  }

  private static void insertFeature(
      JdbcTemplate jdbc,
      UUID id,
      UUID sourceId,
      String objectId,
      Instant startAt,
      Instant staleAt,
      String marker) {
    jdbc.update(
        """
        INSERT INTO geo.features
          (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
        VALUES
          (?, 'TRACK', ?, ?, ?, ?, ?, '{"type":"Point","coordinates":[-73.5,45.5]}', ?::jsonb)
        """,
        id,
        sourceId,
        objectId,
        Timestamp.from(startAt),
        Timestamp.from(startAt),
        Timestamp.from(staleAt),
        """
        {"type":"Feature","properties":{"marker":"%s"}}
        """
            .formatted(marker));
  }

  private static void insertFeatureRow(
      JdbcTemplate jdbc,
      UUID id,
      UUID sourceId,
      String objectId,
      Instant startAt,
      Instant createdAt,
      Instant staleAt,
      String marker) {
    jdbc.update(
        """
        INSERT INTO geo.features
          (id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload)
        VALUES
          (?, 'TRACK', ?, ?, ?, ?, ?, '{"type":"Point","coordinates":[-73.5,45.5]}', ?::jsonb)
        """,
        id,
        sourceId,
        objectId,
        Timestamp.from(createdAt),
        startAt == null ? null : Timestamp.from(startAt),
        Timestamp.from(staleAt),
        """
        {"type":"Feature","properties":{"marker":"%s"}}
        """
            .formatted(marker));
  }

  private static void assertRowsExactlyEqual(
      List<HistoricalSourceEvent> expected, List<HistoricalSourceEvent> actual) {
    assertEquals(
        expected.stream().map(HistoricalSourceEvent::sourceId).toList(),
        actual.stream().map(HistoricalSourceEvent::sourceId).toList());
    assertEquals(
        expected.stream().map(HistoricalSourceEvent::objectId).toList(),
        actual.stream().map(HistoricalSourceEvent::objectId).toList());
    assertEquals(
        expected.stream().map(HistoricalSourceEvent::eventId).toList(),
        actual.stream().map(HistoricalSourceEvent::eventId).toList());
    assertEquals(
        expected.stream().map(HistoricalSourceEvent::playbackAt).toList(),
        actual.stream().map(HistoricalSourceEvent::playbackAt).toList());
    assertEquals(
        expected.stream().map(HistoricalSourceEvent::payloadJson).toList(),
        actual.stream().map(HistoricalSourceEvent::payloadJson).toList());
  }

  private static Map<String, HistoricalSourceEvent> finalStateByObject(
      List<HistoricalSourceEvent> rows) {
    Map<String, HistoricalSourceEvent> state = new LinkedHashMap<>();
    for (HistoricalSourceEvent row : rows) {
      state.put(row.objectId(), row);
    }
    return state;
  }

  private static void assertStateEventIdsEqual(
      Map<String, HistoricalSourceEvent> expected, Map<String, HistoricalSourceEvent> actual) {
    assertEquals(expected.keySet(), actual.keySet());
    for (String objectId : expected.keySet()) {
      assertEquals(expected.get(objectId).eventId(), actual.get(objectId).eventId(), objectId);
      assertEquals(
          expected.get(objectId).payloadJson(), actual.get(objectId).payloadJson(), objectId);
    }
  }

  private static HistoricalTracksJobSubmission job(UUID sourceId) {
    return job(sourceId, HistoricalResolution.MINUTE_1);
  }

  private static HistoricalTracksJobSubmission job(UUID sourceId, HistoricalResolution resolution) {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        sourceId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        resolution,
        132,
        List.of());
  }

  private static HistoricalTracksJobSubmission layerJob(UUID layerId) {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.LAYER,
        layerId,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of());
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        0,
        "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:02:00Z",
        null,
        null,
        null,
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"));
  }

  private static HistoricalChunkPlanEntry nextChunk() {
    return new HistoricalChunkPlanEntry(
        1,
        "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:01:00Z",
        null,
        null,
        null,
        Instant.parse("2026-04-06T10:01:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"));
  }
}
