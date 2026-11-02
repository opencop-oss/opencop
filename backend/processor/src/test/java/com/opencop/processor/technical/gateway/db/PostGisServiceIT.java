// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.provenance.FeatureProvenance;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostGisServiceIT {

  private static final java.util.Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void save_withDuplicateHelperKeys_persistsFeaturesAndOnlyLatestHelperRow() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      createPostgisCompatibilityFunctions(dataSource);
      var service = new PostGisService(dataSource, null);
      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440400");
      UUID olderEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440401");
      UUID newerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440402");
      UUID otherEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440403");

      assertDoesNotThrow(
          () ->
              service.save(
                  List.of(
                      featureRecord(olderEventId, sourceId, "obj-1", "2026-04-16T14:00:00Z", null),
                      featureRecord(newerEventId, sourceId, "obj-1", "2026-04-16T14:01:00Z", null),
                      featureRecord(
                          otherEventId, sourceId, "obj-2", "2026-04-16T14:02:00Z", null))));

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM geo.features", Integer.class));
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.feature_latest_active_helper", Integer.class));
      assertEquals(
          newerEventId,
          jdbc.queryForObject(
              "SELECT event_id FROM geo.feature_latest_active_helper WHERE source = ? AND object_id = ?",
              UUID.class,
              sourceId,
              "obj-1"));
    }
  }

  @Test
  void save_withDuplicateAndIndependentKeys_onlyCollapsesTheDuplicateKey() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      createPostgisCompatibilityFunctions(dataSource);
      var service = new PostGisService(dataSource, null);
      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440410");
      UUID olderDuplicateEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440411");
      UUID newerDuplicateEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440412");
      UUID independentEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440413");

      assertDoesNotThrow(
          () ->
              service.save(
                  List.of(
                      featureRecord(
                          olderDuplicateEventId, sourceId, "obj-dup", "2026-04-16T14:03:00Z", null),
                      featureRecord(
                          newerDuplicateEventId, sourceId, "obj-dup", "2026-04-16T14:04:00Z", null),
                      featureRecord(
                          independentEventId,
                          sourceId,
                          "obj-independent",
                          "2026-04-16T14:05:00Z",
                          null))));

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM geo.features", Integer.class));
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.feature_latest_active_helper", Integer.class));
      assertEquals(
          newerDuplicateEventId,
          jdbc.queryForObject(
              "SELECT event_id FROM geo.feature_latest_active_helper WHERE source = ? AND object_id = ?",
              UUID.class,
              sourceId,
              "obj-dup"));
      assertEquals(
          independentEventId,
          jdbc.queryForObject(
              "SELECT event_id FROM geo.feature_latest_active_helper WHERE source = ? AND object_id = ?",
              UUID.class,
              sourceId,
              "obj-independent"));
    }
  }

  @Test
  void save_withOlderEventAfterLatest_keepsLatestHelperEventAndLowersFirstEmission()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      createPostgisCompatibilityFunctions(dataSource);
      var service = new PostGisService(dataSource, null);
      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440430");
      UUID newerEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440431");
      UUID olderEventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440432");
      String objectId = "obj-older-after-newer";

      service.save(
          List.of(
              featureRecord(
                  newerEventId,
                  sourceId,
                  objectId,
                  "2026-04-16T14:05:00Z",
                  "2026-04-16T14:10:00Z")));
      service.save(
          List.of(
              featureRecord(
                  olderEventId,
                  sourceId,
                  objectId,
                  "2026-04-16T14:00:00Z",
                  "2026-04-16T14:04:00Z")));

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      var helper =
          jdbc.queryForObject(
              """
                  SELECT event_id, playback_at, first_emission_at, stale_at
                  FROM geo.feature_latest_active_helper
                  WHERE source = ? AND object_id = ?
                  """,
              (rs, _) ->
                  new HelperRow(
                      rs.getObject("event_id", UUID.class),
                      rs.getTimestamp("playback_at").toInstant(),
                      rs.getTimestamp("first_emission_at").toInstant(),
                      rs.getTimestamp("stale_at").toInstant()),
              sourceId,
              objectId);

      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM geo.features", Integer.class));
      assertEquals(newerEventId, helper.eventId());
      assertEquals(Instant.parse("2026-04-16T14:05:00Z"), helper.playbackAt());
      assertEquals(Instant.parse("2026-04-16T14:00:00Z"), helper.firstEmissionAt());
      assertEquals(Instant.parse("2026-04-16T14:10:00Z"), helper.staleAt());
    }
  }

  @Test
  void save_withMoreThanOneHundredOrderedHelperRows_persistsAllHelperRowsAcrossExecutionGroups()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      createPostgisCompatibilityFunctions(dataSource);
      var service = new PostGisService(dataSource, null);
      UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440420");
      List<PostGisFeatureRecord> featureRecords = new ArrayList<>();

      for (int index = 0; index < 105; index++) {
        featureRecords.add(
            featureRecord(
                UUID.fromString("550e8400-e29b-41d4-a716-%012d".formatted(446655440500L + index)),
                sourceId,
                "obj-%03d".formatted(index),
                "2026-04-16T14:10:00Z",
                null));
      }

      assertDoesNotThrow(() -> service.save(featureRecords));

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertEquals(105, jdbc.queryForObject("SELECT COUNT(*) FROM geo.features", Integer.class));
      assertEquals(
          105,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.feature_latest_active_helper", Integer.class));
    }
  }

  @Test
  void save_withProvenance_persistsEveryComponent() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      createPostgisCompatibilityFunctions(dataSource);
      var service = new PostGisService(dataSource, null);
      var eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440500");
      var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440501");

      service.save(
          List.of(
              featureRecord(eventId, sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)));

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      var row = provenanceRow(jdbc, eventId);
      assertEquals("DATA_SOURCE", row.get("source_kind"));
      assertEquals(MAPPER_ID, row.get("mapper_id"));
      assertEquals(7, row.get("mapper_version"));
      assertEquals("topic:0:99", row.get("ingress_id"));
      assertEquals("topic:0:99:7", row.get("mapping_execution_id"));
      assertEquals(RECEIVED_AT, ((java.sql.Timestamp) row.get("source_received_at")).toInstant());
    }
  }

  @Test
  void save_withDuplicateCarryingNoProvenance_leavesTheKnownProvenanceIntact() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      DataSource dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      createPostgisCompatibilityFunctions(dataSource);
      var service = new PostGisService(dataSource, null);
      var eventId = UUID.fromString("550e8400-e29b-41d4-a716-446655440502");
      var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440503");

      service.save(
          List.of(
              featureRecord(eventId, sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)));

      // A legacy producer, or a replay through a path that carries no provenance headers, arrives
      // for the same feature id. The conflict update must not erase what is already recorded: null
      // means "not reported", never "known to be absent".
      service.save(
          List.of(
              featureRecord(
                  eventId,
                  sourceId,
                  "obj-1",
                  "2026-04-16T14:05:00Z",
                  null,
                  FeatureProvenance.NONE)));

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      var row = provenanceRow(jdbc, eventId);
      assertEquals("DATA_SOURCE", row.get("source_kind"));
      assertEquals(MAPPER_ID, row.get("mapper_id"));
      assertEquals(7, row.get("mapper_version"));
      assertEquals("topic:0:99", row.get("ingress_id"));
      assertEquals("topic:0:99:7", row.get("mapping_execution_id"));
      assertEquals(RECEIVED_AT, ((java.sql.Timestamp) row.get("source_received_at")).toInstant());
      assertEquals(
          Instant.parse("2026-04-16T14:05:00Z"),
          jdbc.queryForObject(
                  "SELECT start_at FROM geo.features WHERE id = ?",
                  java.sql.Timestamp.class,
                  eventId)
              .toInstant(),
          "the non-provenance columns must still take the newer values");
    }
  }

  private static java.util.Map<String, Object> provenanceRow(JdbcTemplate jdbc, UUID eventId) {
    return jdbc.queryForMap(
        """
        SELECT source_kind, mapper_id, mapper_version, ingress_id, mapping_execution_id,
               source_received_at, mapping_completed_at
          FROM geo.features
         WHERE id = ?
        """,
        eventId);
  }

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554405ff");
  private static final Instant RECEIVED_AT = Instant.parse("2026-04-16T13:59:00Z");
  private static final Instant COMPLETED_AT = Instant.parse("2026-04-16T13:59:30Z");
  private static final FeatureProvenance PROVENANCE =
      new FeatureProvenance(
          FeatureProvenance.FeatureSourceKind.DATA_SOURCE,
          MAPPER_ID,
          7,
          "topic:0:99",
          "topic:0:99:7",
          RECEIVED_AT,
          COMPLETED_AT);

  /** What a Core broadcast carries: the layer is its own source and no mapper ran. */
  private static final FeatureProvenance BROADCAST_PROVENANCE =
      new FeatureProvenance(
          FeatureProvenance.FeatureSourceKind.LAYER,
          null,
          null,
          "550e8400-e29b-41d4-a716-4466554406aa",
          null,
          RECEIVED_AT,
          null);

  private static PostGisFeatureRecord featureRecord(
      UUID eventId, UUID sourceId, String objectId, String startAt, String staleAt) {
    return featureRecord(eventId, sourceId, objectId, startAt, staleAt, FeatureProvenance.NONE);
  }

  private static PostGisFeatureRecord featureRecord(
      UUID eventId,
      UUID sourceId,
      String objectId,
      String startAt,
      String staleAt,
      FeatureProvenance provenance) {
    return new PostGisFeatureRecord(
        eventId,
        "TRACK",
        sourceId,
        null,
        objectId,
        startAt == null ? null : Instant.parse(startAt),
        staleAt == null ? null : Instant.parse(staleAt),
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        "{\"type\":\"Feature\",\"properties\":{\"id\":\"%s\"}}".formatted(eventId),
        provenance);
  }

  // ── Resource activity ──────────────────────────────────────────────────────

  @Test
  void save_recordsMapperActivityOncePerBatch() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE),
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-2", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");

      // Two features, one mapper, one row: activity is aggregated per resource over the batch.
      assertEquals(1, ledgerCount(jdbc, "MAPPING_EXECUTION", "MAPPER", MAPPER_ID));
      assertEquals(COMPLETED_AT, activity(jdbc, "MAPPER", MAPPER_ID));
    }
  }

  @Test
  void save_replayingTheSameBatchAdvancesNothing() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var records =
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE));

      service.save(records, "batch-1");
      service.save(records, "batch-1");

      assertEquals(1, ledgerCount(jdbc, "MAPPING_EXECUTION", "MAPPER", MAPPER_ID));
      assertEquals(COMPLETED_AT, activity(jdbc, "MAPPER", MAPPER_ID));
    }
  }

  @Test
  void save_withNoMapperProvenanceRecordsNoMapperActivity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);

      // A decode that emits no features never reaches persistence at all, so its mapper stays
      // untouched. The nearest thing persistence can be handed is a record carrying no mapper
      // identity, which must skip rather than credit some default.
      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(),
                  sourceId,
                  "obj-1",
                  "2026-04-16T14:00:00Z",
                  null,
                  FeatureProvenance.NONE)),
          "batch-1");

      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM activity.recorded_activity WHERE activity_kind = 'MAPPING_EXECUTION'",
              Integer.class));
      assertNull(activity(jdbc, "MAPPER", MAPPER_ID));
    }
  }

  @Test
  void save_withoutABatchKeyRecordsNoActivity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)));

      assertEquals(
          0, jdbc.queryForObject("SELECT COUNT(*) FROM activity.recorded_activity", Integer.class));
      assertNull(activity(jdbc, "MAPPER", MAPPER_ID));
    }
  }

  @Test
  void save_advancesALayerAssignedTheSourceDirectly() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      assignSourceToLayer(jdbc, sourceId, layerId);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");

      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_advancesALayerReachedThroughADataSourceGroup() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      assignSourceToLayerThroughGroup(jdbc, sourceId, layerId);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");

      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_creditsALayerReachedByBothPathsOnlyOnce() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      assignSourceToLayer(jdbc, sourceId, layerId);
      assignSourceToLayerThroughGroup(jdbc, sourceId, layerId);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");

      // The UNION plus GROUP BY is what collapses the two paths; without it the second insert
      // would conflict on the same identity and silently contribute nothing anyway, so assert the
      // row count rather than only the timestamp.
      assertEquals(1, ledgerCount(jdbc, "LAYER_DELIVERY", "LAYER", layerId));
      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_advancesALayerThatIsItselfTheFeatureSource() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var layerId = insertLayer(jdbc);

      // A drawing, a broadcast, or a clone target: geo.features.source is the layer itself.
      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), layerId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");

      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_advancesALayerFromABroadcastWithoutCreditingAnyMapper() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var layerId = insertLayer(jdbc);

      // The shape Core's broadcast publisher emits for a drawing, edit or delete on a layer: the
      // layer is its own source, a receipt time is carried, and no mapper ran. Drawing on a layer
      // is not a mapper executing, so this must advance the layer and credit no mapper.
      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(),
                  layerId,
                  "obj-1",
                  "2026-04-16T14:00:00Z",
                  null,
                  BROADCAST_PROVENANCE)),
          "batch-1");

      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM activity.recorded_activity WHERE activity_kind = 'MAPPING_EXECUTION'",
              Integer.class));
    }
  }

  @Test
  void save_doesNotAdvanceALayerWithNoRelationshipToTheSource() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var unrelatedLayerId = insertLayer(jdbc);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");

      assertNull(activity(jdbc, "LAYER", unrelatedLayerId));
    }
  }

  @Test
  void save_withASourceAttachedAfterTheBatchIsNotRetroactive() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");
      assignSourceToLayer(jdbc, sourceId, layerId);

      // Membership is whatever the batch's own snapshot said. The layer picks activity up from the
      // next batch, not from one that committed before it could see the source.
      assertNull(activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_replayingAfterAMembershipChangeCreditsTheNewlyAttachedLayer() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      var record =
          featureRecord(
              UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE);

      service.save(List.of(record), "batch-1");
      assignSourceToLayer(jdbc, sourceId, layerId);
      // The at-least-once window: the features committed but the offset did not, so the same batch
      // arrives again after the layer was attached.
      service.save(List.of(record), "batch-1");

      // Current-recipient semantics, pinned deliberately. Membership is recomputed per invocation
      // and resource_id is part of the activity identity, so the newly attached layer takes a fact
      // the original delivery never wrote. Preserving the original recipient set would mean
      // persisting it per batch; the layer can see these features now, so this is accepted.
      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_remappedUnderANewBatchKeyRecordsASecondMapperFact() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var laterCompletion = COMPLETED_AT.plusSeconds(2);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE)),
          "batch-1");
      // A remap keeps mappingExecutionId but takes a fresh mappingCompletedAt, and republishing at
      // new downstream offsets yields a new batch key.
      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(),
                  sourceId,
                  "obj-1",
                  "2026-04-16T14:00:00Z",
                  null,
                  remapOf(PROVENANCE, laterCompletion))),
          "batch-2");

      // Batch keying is deliberate: two facts, and the mapper advances to the retry-time
      // completion. Keying by (mapperId, mappingExecutionId) would collapse these into one, at the
      // cost of a ledger row per record on the persistence path. The value is not wrong either
      // way - the mapper did execute a second time, a retry backoff later.
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM activity.recorded_activity WHERE activity_kind = 'MAPPING_EXECUTION'",
              Integer.class));
      assertEquals(laterCompletion, activity(jdbc, "MAPPER", MAPPER_ID));
    }
  }

  /** The same mapping execution, mapped again: identical identity, a later completion time. */
  private static FeatureProvenance remapOf(FeatureProvenance original, Instant completedAt) {
    return new FeatureProvenance(
        original.sourceKind(),
        original.mapperId(),
        original.mapperVersion(),
        original.ingressId(),
        original.mappingExecutionId(),
        original.sourceReceivedAt(),
        completedAt);
  }

  @Test
  void save_recordsTheNewestTimestampsOfSeveralRecordsInOneBatch() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      assignSourceToLayer(jdbc, sourceId, layerId);
      var newestReceived = RECEIVED_AT.plusSeconds(120);
      var newestCompleted = COMPLETED_AT.plusSeconds(120);

      // The newest pair sits in the middle of the batch deliberately: with it at either end, a
      // first-wins or last-wins aggregation would pass by accident.
      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(),
                  sourceId,
                  "obj-1",
                  "2026-04-16T14:00:00Z",
                  null,
                  provenanceWith(RECEIVED_AT, COMPLETED_AT)),
              featureRecord(
                  UUID.randomUUID(),
                  sourceId,
                  "obj-2",
                  "2026-04-16T14:00:00Z",
                  null,
                  provenanceWith(newestReceived, newestCompleted)),
              featureRecord(
                  UUID.randomUUID(),
                  sourceId,
                  "obj-3",
                  "2026-04-16T14:00:00Z",
                  null,
                  provenanceWith(RECEIVED_AT.plusSeconds(60), COMPLETED_AT.plusSeconds(60)))),
          "batch-1");

      assertEquals(
          newestCompleted,
          activity(jdbc, "MAPPER", MAPPER_ID),
          "a mapper's activity is the newest completion in the batch, not the first or last seen");
      assertEquals(
          newestReceived,
          activity(jdbc, "LAYER", layerId),
          "a layer's activity is the newest ingress reaching it, not the first or last seen");
    }
  }

  @Test
  void save_replayingTheSameBatchAdvancesTheLayerOnlyOnce() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      assignSourceToLayer(jdbc, sourceId, layerId);
      var records =
          List.of(
              featureRecord(
                  UUID.randomUUID(), sourceId, "obj-1", "2026-04-16T14:00:00Z", null, PROVENANCE));

      service.save(records, "batch-1");
      service.save(records, "batch-1");

      // The delivery identity has to be stable across the fan-out's UNION branches for a redelivery
      // to conflict; an identity that varied per branch would duplicate rows here instead.
      assertEquals(1, ledgerCount(jdbc, "LAYER_DELIVERY", "LAYER", layerId));
      assertEquals(RECEIVED_AT, activity(jdbc, "LAYER", layerId));
    }
  }

  @Test
  void save_clampsFutureDatedProvenanceRatherThanPinningTheProjection() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = readyDatabase(postgres);
      var service = new PostGisService(postgres.getPostgresDatabase(), null);
      var sourceId = insertDataSource(jdbc);
      var layerId = insertLayer(jdbc);
      assignSourceToLayer(jdbc, sourceId, layerId);
      var future = Instant.now().plusSeconds(3600);

      service.save(
          List.of(
              featureRecord(
                  UUID.randomUUID(),
                  sourceId,
                  "obj-1",
                  "2026-04-16T14:00:00Z",
                  null,
                  provenanceWith(future, future))),
          "batch-1");

      // Both projections advance monotonically, so an unclamped future instant from a skewed
      // producer would pin the resource permanently and reject every later correct write.
      assertTrue(
          activity(jdbc, "MAPPER", MAPPER_ID).isBefore(future),
          "a future-dated mapping completion must be clamped to the database's own clock");
      assertTrue(
          activity(jdbc, "LAYER", layerId).isBefore(future),
          "a future-dated ingress time must be clamped to the database's own clock");
    }
  }

  private static FeatureProvenance provenanceWith(Instant receivedAt, Instant completedAt) {
    return new FeatureProvenance(
        FeatureProvenance.FeatureSourceKind.DATA_SOURCE,
        MAPPER_ID,
        7,
        "topic:0:99",
        "topic:0:99:7",
        receivedAt,
        completedAt);
  }

  private static JdbcTemplate readyDatabase(EmbeddedPostgres postgres) {
    DataSource dataSource = postgres.getPostgresDatabase();
    migrateAll(dataSource);
    createPostgisCompatibilityFunctions(dataSource);
    return new JdbcTemplate(dataSource);
  }

  private static Instant activity(JdbcTemplate jdbc, String resourceKind, UUID resourceId) {
    var rows =
        jdbc.queryForList(
            """
            SELECT last_event_at
              FROM activity.resource_activity
             WHERE resource_kind = ? AND resource_id = ?
            """,
            java.sql.Timestamp.class,
            resourceKind,
            resourceId);
    return rows.isEmpty() ? null : rows.getFirst().toInstant();
  }

  private static int ledgerCount(
      JdbcTemplate jdbc, String activityKind, String resourceKind, UUID resourceId) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*)
          FROM activity.recorded_activity
         WHERE activity_kind = ? AND resource_kind = ? AND resource_id = ?
        """,
        Integer.class,
        activityKind,
        resourceKind,
        resourceId);
  }

  private static UUID insertLayer(JdbcTemplate jdbc) {
    var layerId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    return layerId;
  }

  private static void assignSourceToLayer(JdbcTemplate jdbc, UUID sourceId, UUID layerId) {
    jdbc.update(
        """
        INSERT INTO iam.server_layer_access (server_id, layer_id, role)
        VALUES (?, ?, CAST('VIEWER' AS iam.server_role))
        """,
        sourceId,
        layerId);
  }

  private static void assignSourceToLayerThroughGroup(
      JdbcTemplate jdbc, UUID sourceId, UUID layerId) {
    var groupId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO iam.data_source_group (id, name) VALUES (?, ?)", groupId, "group-" + groupId);
    jdbc.update(
        "INSERT INTO iam.data_source_group_data_source (data_source_group_id, data_source_id) VALUES (?, ?)",
        groupId,
        sourceId);
    jdbc.update(
        "INSERT INTO iam.layer_data_source_group (layer_id, data_source_group_id) VALUES (?, ?)",
        layerId,
        groupId);
  }

  /** The full chain a data source needs: its mapper, then the source itself. */
  private static UUID insertDataSource(JdbcTemplate jdbc) {
    var mapperId = MAPPER_ID;
    var sourceId = UUID.randomUUID();

    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, mapper)
        VALUES (?, 1, ?, CAST('{}' AS jsonb))
            ON CONFLICT (id, version) DO NOTHING
        """,
        mapperId,
        "mapper-" + mapperId);
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, tenant, provider, ingress_mapper_id, connection_spec,
           integration_id, connection_schema_version)
        VALUES (?, ?, 'default', CAST('TCP' AS profiles.transport_kind), ?, CAST('{}' AS jsonb),
                ?, ?)
        """,
        sourceId,
        "source-" + sourceId,
        mapperId,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
    return sourceId;
  }

  private record HelperRow(
      UUID eventId, Instant playbackAt, Instant firstEmissionAt, Instant staleAt) {}

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

  private static void createPostgisCompatibilityFunctions(DataSource dataSource) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION public.ST_GeomFromGeoJSON(text) RETURNS text LANGUAGE SQL IMMUTABLE AS $$ SELECT $1; $$");
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION public.ST_SetSRID(text, integer) RETURNS text LANGUAGE SQL IMMUTABLE AS $$ SELECT $1; $$");
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION public.ST_Force3D(text) RETURNS text LANGUAGE SQL IMMUTABLE AS $$ SELECT $1; $$");
  }
}
