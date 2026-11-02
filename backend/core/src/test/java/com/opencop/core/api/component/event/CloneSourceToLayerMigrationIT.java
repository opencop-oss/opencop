// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class CloneSourceToLayerMigrationIT {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void cloneSourceToLayer_whenCopyAll_clonesFeaturesRemarksAndMediaWithRewrittenIds()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
      var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
      var objectA = "clone-object-a";
      var objectB = "clone-object-b";
      var featureAId = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
      var featureBId = UUID.fromString("550e8400-e29b-41d4-a716-446655440104");
      var eventMediaId = UUID.fromString("550e8400-e29b-41d4-a716-446655440105");
      var layerMediaId = UUID.fromString("550e8400-e29b-41d4-a716-446655440106");
      var eventThumbnailId = UUID.fromString("550e8400-e29b-41d4-a716-446655440107");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertUser(jdbc, userId);
      insertFeature(
          jdbc, featureAId, objectA, sourceLayerId, Instant.parse("2026-03-12T10:00:00Z"));
      insertFeature(
          jdbc, featureBId, objectB, sourceLayerId, Instant.parse("2026-03-12T11:00:00Z"));
      insertRemark(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440108"),
          sourceLayerId,
          objectA,
          userId,
          "remark-a");
      insertRemark(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655440109"),
          sourceLayerId,
          objectB,
          userId,
          "remark-b");
      insertMedia(
          jdbc,
          eventMediaId,
          userId,
          "blob/event-original",
          "event-original.png",
          null,
          "EVENT_DETAIL");
      insertMedia(
          jdbc,
          eventThumbnailId,
          userId,
          "blob/event-thumbnail",
          "event-thumbnail.png",
          eventMediaId,
          "EVENT_DETAIL");
      insertMedia(
          jdbc,
          layerMediaId,
          userId,
          "blob/layer-original",
          "layer-original.png",
          null,
          "LAYER_ICON");
      insertEventMedia(jdbc, sourceLayerId, objectA, eventMediaId);
      insertLayerMedia(jdbc, sourceLayerId, layerMediaId);

      callClone(jdbc, sourceLayerId, targetLayerId);

      var targetFeatures =
          jdbc.queryForList(
              "SELECT id, source, object_id, payload FROM geo.features WHERE source = ? ORDER BY object_id",
              targetLayerId);
      assertEquals(2, targetFeatures.size());
      assertEquals(Set.of(objectA, objectB), extractObjectIds(targetFeatures));

      for (var row : targetFeatures) {
        var featureId = (UUID) row.get("id");
        assertNotEquals(featureAId, featureId);
        assertNotEquals(featureBId, featureId);
        assertEquals(targetLayerId, row.get("source"));

        JsonNode payload = OBJECT_MAPPER.readTree(asJson(row.get("payload")));
        JsonNode openCop = getOpenCopNode(payload);
        assertEquals(featureId.toString(), payload.path("id").asText());
        assertEquals(featureId.toString(), openCop.path("id").asText());
        assertEquals(targetLayerId.toString(), payload.path("source").asText());
        assertEquals(targetLayerId.toString(), openCop.path("source").asText());
        assertEquals(row.get("object_id"), openCop.path("objectId").asText());
      }

      assertPayloadsMatchExceptIdentifiers(jdbc, sourceLayerId, targetLayerId);

      var targetRemarks =
          jdbc.queryForList(
              "SELECT source_id, object_id, content FROM geo.remarks WHERE source_id = ? ORDER BY object_id",
              targetLayerId);
      assertEquals(2, targetRemarks.size());
      assertEquals(objectA, targetRemarks.get(0).get("object_id"));
      assertEquals(objectB, targetRemarks.get(1).get("object_id"));

      var targetEventMedia =
          jdbc.queryForList(
              """
              SELECT ea.object_id, ea.attachment_id AS media_id, mf.blob_key, mf.original_filename
              FROM event_attachment ea
              JOIN media_file mf ON mf.id = ea.attachment_id
              WHERE ea.source_id = ?
              ORDER BY ea.object_id, mf.original_filename
              """,
              targetLayerId);
      assertEquals(1, targetEventMedia.size());
      assertEquals(objectA, targetEventMedia.get(0).get("object_id"));
      assertNotEquals(eventMediaId, targetEventMedia.get(0).get("media_id"));
      assertEquals("blob/event-original", targetEventMedia.get(0).get("blob_key"));

      var targetLayerMedia =
          jdbc.queryForList(
              """
              SELECT lm.media_id, mf.blob_key, mf.original_filename
              FROM layer_media lm
              JOIN media_file mf ON mf.id = lm.media_id
              WHERE lm.layer_id = ?
              ORDER BY mf.original_filename
              """,
              targetLayerId);
      assertEquals(1, targetLayerMedia.size());
      assertNotEquals(layerMediaId, targetLayerMedia.get(0).get("media_id"));
      assertEquals("blob/layer-original", targetLayerMedia.get(0).get("blob_key"));

      var clonedMediaRows =
          jdbc.queryForList(
              """
              SELECT id, blob_key, original_filename, thumbnail_of
              FROM media_file
              WHERE original_filename IN ('event-original.png', 'event-thumbnail.png', 'layer-original.png')
              ORDER BY original_filename, id
              """);
      assertEquals(6, clonedMediaRows.size());

      var mediaByFilename = groupMediaByFilename(clonedMediaRows);
      assertEquals(2, mediaByFilename.get("event-original.png").size());
      assertEquals(2, mediaByFilename.get("event-thumbnail.png").size());
      assertEquals(2, mediaByFilename.get("layer-original.png").size());

      UUID clonedEventOriginalId =
          findClonedId(mediaByFilename.get("event-original.png"), eventMediaId);
      UUID clonedEventThumbnailId =
          findClonedId(mediaByFilename.get("event-thumbnail.png"), eventThumbnailId);
      assertEquals(
          clonedEventOriginalId,
          mediaByFilename.get("event-thumbnail.png").stream()
              .filter(row -> row.id().equals(clonedEventThumbnailId))
              .findFirst()
              .orElseThrow()
              .thumbnailOf());
    }
  }

  @Test
  void cloneSourceToLayer_whenCopyAllWithMultipleFeatureAssociations_clonesAllFanoutCorrectly()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440190");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440191");
      var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440192");
      var objectA = "fanout-object-a";
      var objectB = "fanout-object-b";
      var objectC = "fanout-object-c";
      var featureAId = UUID.fromString("550e8400-e29b-41d4-a716-446655440193");
      var featureBId = UUID.fromString("550e8400-e29b-41d4-a716-446655440194");
      var featureCId = UUID.fromString("550e8400-e29b-41d4-a716-446655440195");
      var mediaA1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440196");
      var mediaA2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440197");
      var mediaB1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440198");
      var layerMedia1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440199");
      var layerMedia2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440200");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertUser(jdbc, userId);
      insertFeature(
          jdbc, featureAId, objectA, sourceLayerId, Instant.parse("2026-03-12T10:00:00Z"));
      insertFeature(
          jdbc, featureBId, objectB, sourceLayerId, Instant.parse("2026-03-12T11:00:00Z"));
      insertFeature(
          jdbc, featureCId, objectC, sourceLayerId, Instant.parse("2026-03-12T12:00:00Z"));

      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, objectA, userId, "remark-a-1");
      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, objectA, userId, "remark-a-2");
      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, objectB, userId, "remark-b-1");
      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, objectC, userId, "remark-c-1");

      insertMedia(jdbc, mediaA1, userId, "blob/event-a-1", "event-a-1.png", null, "EVENT_DETAIL");
      insertMedia(jdbc, mediaA2, userId, "blob/event-a-2", "event-a-2.png", null, "EVENT_DETAIL");
      insertMedia(jdbc, mediaB1, userId, "blob/event-b-1", "event-b-1.png", null, "EVENT_DETAIL");
      insertMedia(jdbc, layerMedia1, userId, "blob/layer-1", "layer-1.png", null, "LAYER_ICON");
      insertMedia(jdbc, layerMedia2, userId, "blob/layer-2", "layer-2.png", null, "LAYER_ICON");

      insertEventMedia(jdbc, sourceLayerId, objectA, mediaA1);
      insertEventMedia(jdbc, sourceLayerId, objectA, mediaA2);
      insertEventMedia(jdbc, sourceLayerId, objectB, mediaB1);
      insertLayerMedia(jdbc, sourceLayerId, layerMedia1);
      insertLayerMedia(jdbc, sourceLayerId, layerMedia2);

      callClone(jdbc, sourceLayerId, targetLayerId);

      assertEquals(
          3,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.features WHERE source = ?", Integer.class, targetLayerId));
      assertPayloadsMatchExceptIdentifiers(jdbc, sourceLayerId, targetLayerId);

      var remarkCounts =
          jdbc.queryForList(
              """
              SELECT object_id, COUNT(*) AS remark_count
              FROM geo.remarks
              WHERE source_id = ?
              GROUP BY object_id
              ORDER BY object_id
              """,
              targetLayerId);
      assertEquals(3, remarkCounts.size());
      assertEquals(objectA, remarkCounts.get(0).get("object_id"));
      assertEquals(2L, ((Number) remarkCounts.get(0).get("remark_count")).longValue());
      assertEquals(objectB, remarkCounts.get(1).get("object_id"));
      assertEquals(1L, ((Number) remarkCounts.get(1).get("remark_count")).longValue());
      assertEquals(objectC, remarkCounts.get(2).get("object_id"));
      assertEquals(1L, ((Number) remarkCounts.get(2).get("remark_count")).longValue());

      var eventMediaCounts =
          jdbc.queryForList(
              """
              SELECT object_id, COUNT(*) AS media_count
              FROM event_attachment
              WHERE source_id = ?
              GROUP BY object_id
              ORDER BY object_id
              """,
              targetLayerId);
      assertEquals(2, eventMediaCounts.size());
      assertEquals(objectA, eventMediaCounts.get(0).get("object_id"));
      assertEquals(2L, ((Number) eventMediaCounts.get(0).get("media_count")).longValue());
      assertEquals(objectB, eventMediaCounts.get(1).get("object_id"));
      assertEquals(1L, ((Number) eventMediaCounts.get(1).get("media_count")).longValue());

      var targetEventMedia =
          jdbc.queryForList(
              """
              SELECT ea.object_id, ea.attachment_id AS media_id, mf.blob_key, mf.original_filename
              FROM event_attachment ea
              JOIN media_file mf ON mf.id = ea.attachment_id
              WHERE ea.source_id = ?
              ORDER BY ea.object_id, mf.original_filename
              """,
              targetLayerId);
      assertEquals(3, targetEventMedia.size());
      assertEquals(
          Set.of("blob/event-a-1", "blob/event-a-2", "blob/event-b-1"),
          targetEventMedia.stream()
              .map(row -> (String) row.get("blob_key"))
              .collect(Collectors.toSet()));
      assertTrue(
          targetEventMedia.stream()
              .map(row -> (UUID) row.get("media_id"))
              .noneMatch(Set.of(mediaA1, mediaA2, mediaB1)::contains));

      var targetLayerMedia =
          jdbc.queryForList(
              """
              SELECT lm.media_id, mf.blob_key
              FROM layer_media lm
              JOIN media_file mf ON mf.id = lm.media_id
              WHERE lm.layer_id = ?
              ORDER BY mf.blob_key
              """,
              targetLayerId);
      assertEquals(2, targetLayerMedia.size());
      assertEquals(
          Set.of("blob/layer-1", "blob/layer-2"),
          targetLayerMedia.stream()
              .map(row -> (String) row.get("blob_key"))
              .collect(Collectors.toSet()));
      assertTrue(
          targetLayerMedia.stream()
              .map(row -> (UUID) row.get("media_id"))
              .noneMatch(Set.of(layerMedia1, layerMedia2)::contains));
    }
  }

  @Test
  void cloneSourceToLayer_whenCopyFlagsDisabled_copiesOnlyFeatures() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440110");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440111");
      var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440112");
      var mediaId = UUID.fromString("550e8400-e29b-41d4-a716-446655440113");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertUser(jdbc, userId);
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "clone-object",
          sourceLayerId,
          Instant.parse("2026-03-12T10:00:00Z"));
      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, "clone-object", userId, "remark");
      insertMedia(jdbc, mediaId, userId, "blob/original", "original.png", null, "EVENT_DETAIL");
      insertEventMedia(jdbc, sourceLayerId, "clone-object", mediaId);
      insertLayerMedia(jdbc, sourceLayerId, mediaId);

      callClone(jdbc, sourceLayerId, targetLayerId, false, false);

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.features WHERE source = ?", Integer.class, targetLayerId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.remarks WHERE source_id = ?",
              Integer.class,
              targetLayerId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM event_attachment WHERE source_id = ?",
              Integer.class,
              targetLayerId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM layer_media WHERE layer_id = ?", Integer.class, targetLayerId));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
    }
  }

  @Test
  void cloneSourceToLayer_whenSourceHasHelperRows_clonesMappedHelperRowsWithTargetFeatureIds()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440210");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440211");
      var activeFeatureId = UUID.fromString("550e8400-e29b-41d4-a716-446655440212");
      var staleFeatureId = UUID.fromString("550e8400-e29b-41d4-a716-446655440213");
      var orphanFeatureId = UUID.fromString("550e8400-e29b-41d4-a716-446655440214");
      var activeObjectId = "helper-active-object";
      var staleObjectId = "helper-stale-object";
      var orphanObjectId = "helper-orphan-object";
      var activePlaybackAt = Instant.parse("2026-03-12T10:15:00Z");
      var activeCreatedAt = Instant.parse("2026-03-12T10:16:00Z");
      var activeFirstEmissionAt = Instant.parse("2026-03-12T10:00:00Z");
      var stalePlaybackAt = Instant.parse("2026-03-12T11:15:00Z");
      var staleCreatedAt = Instant.parse("2026-03-12T11:16:00Z");
      var staleAt = Instant.parse("2026-03-12T12:00:00Z");
      var staleFirstEmissionAt = Instant.parse("2026-03-12T11:00:00Z");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertFeature(jdbc, activeFeatureId, activeObjectId, sourceLayerId, activeFirstEmissionAt);
      insertFeature(jdbc, staleFeatureId, staleObjectId, sourceLayerId, staleFirstEmissionAt);
      insertActiveHelper(
          jdbc,
          sourceLayerId,
          activeObjectId,
          activeFeatureId,
          activePlaybackAt,
          activeCreatedAt,
          null,
          activeFirstEmissionAt);
      insertActiveHelper(
          jdbc,
          sourceLayerId,
          staleObjectId,
          staleFeatureId,
          stalePlaybackAt,
          staleCreatedAt,
          staleAt,
          staleFirstEmissionAt);
      insertActiveHelper(
          jdbc,
          sourceLayerId,
          orphanObjectId,
          orphanFeatureId,
          Instant.parse("2026-03-12T13:15:00Z"),
          Instant.parse("2026-03-12T13:16:00Z"),
          null,
          Instant.parse("2026-03-12T13:00:00Z"));

      callClone(jdbc, sourceLayerId, targetLayerId, false, false);

      var targetHelpers =
          jdbc.queryForList(
              """
              SELECT helper.object_id, helper.event_id, helper.playback_at, helper.created_at,
                     helper.stale_at, helper.first_emission_at, features.source AS feature_source
              FROM geo.feature_latest_active_helper helper
              JOIN geo.features features ON features.id = helper.event_id
              WHERE helper.source = ?
              ORDER BY helper.object_id
              """,
              targetLayerId);

      assertEquals(2, targetHelpers.size());
      var activeHelper = targetHelpers.get(0);
      assertEquals(activeObjectId, activeHelper.get("object_id"));
      assertNotEquals(activeFeatureId, activeHelper.get("event_id"));
      assertEquals(targetLayerId, activeHelper.get("feature_source"));
      assertEquals(Timestamp.from(activePlaybackAt), activeHelper.get("playback_at"));
      assertEquals(Timestamp.from(activeCreatedAt), activeHelper.get("created_at"));
      assertEquals(null, activeHelper.get("stale_at"));
      assertEquals(Timestamp.from(activeFirstEmissionAt), activeHelper.get("first_emission_at"));

      var staleHelper = targetHelpers.get(1);
      assertEquals(staleObjectId, staleHelper.get("object_id"));
      assertNotEquals(staleFeatureId, staleHelper.get("event_id"));
      assertEquals(targetLayerId, staleHelper.get("feature_source"));
      assertEquals(Timestamp.from(stalePlaybackAt), staleHelper.get("playback_at"));
      assertEquals(Timestamp.from(staleCreatedAt), staleHelper.get("created_at"));
      assertEquals(Timestamp.from(staleAt), staleHelper.get("stale_at"));
      assertEquals(Timestamp.from(staleFirstEmissionAt), staleHelper.get("first_emission_at"));

      assertPayloadsMatchExceptIdentifiers(jdbc, sourceLayerId, targetLayerId);
    }
  }

  @Test
  void cloneSourceToLayer_whenOnlyRemarksEnabled_skipsMediaClone() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
      var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
      var mediaId = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertUser(jdbc, userId);
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "clone-object",
          sourceLayerId,
          Instant.parse("2026-03-12T10:00:00Z"));
      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, "clone-object", userId, "remark");
      insertMedia(jdbc, mediaId, userId, "blob/original", "original.png", null, "EVENT_DETAIL");
      insertEventMedia(jdbc, sourceLayerId, "clone-object", mediaId);
      insertLayerMedia(jdbc, sourceLayerId, mediaId);

      callClone(jdbc, sourceLayerId, targetLayerId, true, false);

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.remarks WHERE source_id = ?",
              Integer.class,
              targetLayerId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM event_attachment WHERE source_id = ?",
              Integer.class,
              targetLayerId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM layer_media WHERE layer_id = ?", Integer.class, targetLayerId));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
    }
  }

  @Test
  void cloneSourceToLayer_whenOnlyMediaEnabled_skipsRemarkClone() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440131");
      var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440132");
      var mediaId = UUID.fromString("550e8400-e29b-41d4-a716-446655440133");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertUser(jdbc, userId);
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "clone-object",
          sourceLayerId,
          Instant.parse("2026-03-12T10:00:00Z"));
      insertRemark(jdbc, UUID.randomUUID(), sourceLayerId, "clone-object", userId, "remark");
      insertMedia(jdbc, mediaId, userId, "blob/original", "original.png", null, "EVENT_DETAIL");
      insertEventMedia(jdbc, sourceLayerId, "clone-object", mediaId);
      insertLayerMedia(jdbc, sourceLayerId, mediaId);

      callClone(jdbc, sourceLayerId, targetLayerId, false, true);

      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.remarks WHERE source_id = ?",
              Integer.class,
              targetLayerId));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM event_attachment WHERE source_id = ?",
              Integer.class,
              targetLayerId));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM layer_media WHERE layer_id = ?", Integer.class, targetLayerId));
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
    }
  }

  @Test
  void cloneSourceToLayer_whenCopyMediaEnabledAndNoMediaPresent_succeeds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440140");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440141");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "clone-object",
          sourceLayerId,
          Instant.parse("2026-03-12T10:00:00Z"));

      callClone(jdbc, sourceLayerId, targetLayerId, false, true);

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.features WHERE source = ?", Integer.class, targetLayerId));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM media_file", Integer.class));
    }
  }

  @Test
  void cloneSourceToLayer_whenSourceAndTargetMatch_raisesException() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var layerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440150");

      insertLayer(jdbc, layerId, "layer");
      insertFeature(
          jdbc, UUID.randomUUID(), "clone-object", layerId, Instant.parse("2026-03-12T10:00:00Z"));

      var ex = assertThrows(DataAccessException.class, () -> callClone(jdbc, layerId, layerId));
      assertPostgresMessage(ex, "source id and target layer id must differ");
    }
  }

  @Test
  void cloneSourceToLayer_whenTargetLayerMissing_raisesException() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440160");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440161");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "clone-object",
          sourceLayerId,
          Instant.parse("2026-03-12T10:00:00Z"));

      var ex =
          assertThrows(
              DataAccessException.class, () -> callClone(jdbc, sourceLayerId, targetLayerId));
      assertPostgresMessage(ex, "target layer " + targetLayerId + " does not exist");
    }
  }

  @Test
  void cloneSourceToLayer_whenSourceHasNoFeatures_raisesException() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440170");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440171");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");

      var ex =
          assertThrows(
              DataAccessException.class, () -> callClone(jdbc, sourceLayerId, targetLayerId));
      assertPostgresMessage(ex, "source " + sourceLayerId + " has no features to clone");
    }
  }

  @Test
  void cloneSourceToLayer_whenTargetAlreadyHasFeatures_raisesException() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440180");
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440181");

      insertLayer(jdbc, sourceLayerId, "source-layer");
      insertLayer(jdbc, targetLayerId, "target-layer");
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "source-object",
          sourceLayerId,
          Instant.parse("2026-03-12T10:00:00Z"));
      insertFeature(
          jdbc,
          UUID.randomUUID(),
          "target-object",
          targetLayerId,
          Instant.parse("2026-03-12T11:00:00Z"));

      var ex =
          assertThrows(
              DataAccessException.class, () -> callClone(jdbc, sourceLayerId, targetLayerId));
      assertPostgresMessage(ex, "target layer " + targetLayerId + " already has features");
    }
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static void insertLayer(JdbcTemplate jdbc, UUID layerId, String name) {
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, name);
  }

  private static void insertFeature(
      JdbcTemplate jdbc, UUID id, String objectId, UUID sourceId, Instant createdAt) {
    var properties = new OpenCopProperties();
    properties.setId(id);
    properties.setObjectId(objectId);
    properties.setSource(sourceId);
    Feature feature = OpenCopPropertiesParser.writeToFeature(properties, null, id.toString());

    try {
      jdbc.update(
          "INSERT INTO geo.features (id, source, object_id, created_at, start_at, geom, payload) "
              + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
          id,
          sourceId,
          objectId,
          Timestamp.from(createdAt),
          Timestamp.from(createdAt),
          "POINT Z (0 0 0)",
          OBJECT_MAPPER.writeValueAsString(feature));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to insert feature fixture", e);
    }
  }

  private static void insertRemark(
      JdbcTemplate jdbc,
      UUID remarkId,
      UUID sourceId,
      String objectId,
      UUID userId,
      String content) {
    jdbc.update(
        """
        INSERT INTO geo.remarks (id, source_id, object_id, content, user_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        remarkId,
        sourceId,
        objectId,
        content,
        userId,
        Timestamp.from(Instant.parse("2026-03-12T12:00:00Z")),
        Timestamp.from(Instant.parse("2026-03-12T12:05:00Z")));
  }

  private static void insertUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId,
        true,
        false);
  }

  private static void insertMedia(
      JdbcTemplate jdbc,
      UUID mediaId,
      UUID userId,
      String blobKey,
      String originalFilename,
      UUID thumbnailOf,
      String usage) {
    jdbc.update(
        """
        INSERT INTO media_attachment (
            id, creator_id, owner_id, access, created_at, usage
        ) VALUES (?, ?, ?, CAST(? AS media_access), now(), CAST(? AS media_usage))
        """,
        mediaId,
        userId,
        userId,
        "LOGGED_IN",
        usage);
    jdbc.update(
        """
        INSERT INTO media_file (
            id, blob_key, original_filename, content_type, size_bytes, mime_type, thumbnail_of
        ) VALUES (?, ?, ?, CAST(? AS media_content_type), ?, ?, ?)
        """,
        mediaId,
        blobKey,
        originalFilename,
        "IMAGE",
        42L,
        "image/png",
        thumbnailOf);
  }

  private static void insertEventMedia(
      JdbcTemplate jdbc, UUID sourceId, String objectId, UUID mediaId) {
    jdbc.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        sourceId,
        objectId,
        mediaId);
  }

  private static void insertActiveHelper(
      JdbcTemplate jdbc,
      UUID sourceId,
      String objectId,
      UUID eventId,
      Instant playbackAt,
      Instant createdAt,
      Instant staleAt,
      Instant firstEmissionAt) {
    jdbc.update(
        """
        INSERT INTO geo.feature_latest_active_helper (
            source, object_id, event_id, playback_at, created_at, stale_at, first_emission_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        sourceId,
        objectId,
        eventId,
        Timestamp.from(playbackAt),
        Timestamp.from(createdAt),
        staleAt != null ? Timestamp.from(staleAt) : null,
        Timestamp.from(firstEmissionAt));
  }

  private static void insertLayerMedia(JdbcTemplate jdbc, UUID layerId, UUID mediaId) {
    jdbc.update("INSERT INTO layer_media (layer_id, media_id) VALUES (?, ?)", layerId, mediaId);
  }

  private static void callClone(JdbcTemplate jdbc, UUID sourceId, UUID targetLayerId) {
    jdbc.update("CALL geo.clone_source_to_layer(?, ?)", sourceId, targetLayerId);
  }

  private static void callClone(
      JdbcTemplate jdbc,
      UUID sourceId,
      UUID targetLayerId,
      boolean copyRemarks,
      boolean copyMedia) {
    jdbc.update(
        "CALL geo.clone_source_to_layer(?, ?, ?, ?)",
        sourceId,
        targetLayerId,
        copyRemarks,
        copyMedia);
  }

  private static Set<String> extractObjectIds(List<Map<String, Object>> rows) {
    return rows.stream().map(row -> (String) row.get("object_id")).collect(Collectors.toSet());
  }

  private static Map<String, List<MediaRow>> groupMediaByFilename(List<Map<String, Object>> rows) {
    var grouped = new HashMap<String, List<MediaRow>>();
    for (var row : rows) {
      grouped
          .computeIfAbsent((String) row.get("original_filename"), ignored -> new ArrayList<>())
          .add(
              new MediaRow(
                  (UUID) row.get("id"),
                  (String) row.get("blob_key"),
                  (String) row.get("original_filename"),
                  (UUID) row.get("thumbnail_of")));
    }
    grouped.values().forEach(list -> list.sort(Comparator.comparing(MediaRow::id)));
    return grouped;
  }

  private static UUID findClonedId(List<MediaRow> rows, UUID originalId) {
    return rows.stream()
        .map(MediaRow::id)
        .filter(id -> !id.equals(originalId))
        .findFirst()
        .orElseThrow();
  }

  private static void assertPostgresMessage(DataAccessException ex, String messageFragment) {
    Throwable root = ex.getMostSpecificCause();
    assertInstanceOf(PSQLException.class, root);
    assertTrue(root.getMessage().contains(messageFragment), root.getMessage());
  }

  private static void assertPayloadsMatchExceptIdentifiers(
      JdbcTemplate jdbc, UUID sourceLayerId, UUID targetLayerId) throws Exception {
    var sourceFeatures =
        jdbc.queryForList(
            "SELECT object_id, payload FROM geo.features WHERE source = ? ORDER BY object_id",
            sourceLayerId);
    var targetFeatures =
        jdbc.queryForList(
            "SELECT id, object_id, payload FROM geo.features WHERE source = ? ORDER BY object_id",
            targetLayerId);

    assertEquals(sourceFeatures.size(), targetFeatures.size());

    var sourcePayloadsByObjectId = payloadsByObjectId(sourceFeatures);
    for (var targetRow : targetFeatures) {
      var objectId = (String) targetRow.get("object_id");
      var sourcePayload = sourcePayloadsByObjectId.get(objectId);
      assertNotNull(sourcePayload, "missing source payload for " + objectId);

      JsonNode targetPayload = OBJECT_MAPPER.readTree(asJson(targetRow.get("payload")));
      var targetFeatureId = (UUID) targetRow.get("id");

      assertEquals(
          normalizePayload(sourcePayload, targetFeatureId, targetLayerId),
          normalizePayload(targetPayload, targetFeatureId, targetLayerId));
    }
  }

  private static Map<String, JsonNode> payloadsByObjectId(List<Map<String, Object>> rows)
      throws Exception {
    var payloads = new HashMap<String, JsonNode>();
    for (var row : rows) {
      payloads.put(
          (String) row.get("object_id"), OBJECT_MAPPER.readTree(asJson(row.get("payload"))));
    }
    return payloads;
  }

  private static JsonNode normalizePayload(JsonNode payload, UUID featureId, UUID sourceId) {
    var normalized = payload.deepCopy();
    setTextValue(normalized, "id", featureId.toString());
    setTextValue(normalized, "source", sourceId.toString());
    setTextValue(normalized, "opencop", "id", featureId.toString());
    setTextValue(normalized, "opencop", "source", sourceId.toString());
    setTextValue(normalized, "properties", "opencop", "id", featureId.toString());
    setTextValue(normalized, "properties", "opencop", "source", sourceId.toString());
    return normalized;
  }

  private static void setTextValue(JsonNode node, String... pathAndValue) {
    var value = pathAndValue[pathAndValue.length - 1];
    var path = List.of(pathAndValue).subList(0, pathAndValue.length - 1);
    if (path.isEmpty()) {
      return;
    }

    JsonNode cursor = node;
    for (int i = 0; i < path.size() - 1; i++) {
      cursor = cursor.path(path.get(i));
      if (cursor.isMissingNode()) {
        return;
      }
    }

    if (cursor.isObject()) {
      ((tools.jackson.databind.node.ObjectNode) cursor).put(path.get(path.size() - 1), value);
    }
  }

  private static JsonNode getOpenCopNode(JsonNode payload) {
    var topLevel = payload.path("opencop");
    if (!topLevel.isMissingNode()) {
      return topLevel;
    }
    return payload.path("properties").path("opencop");
  }

  private static String asJson(Object value) {
    if (value instanceof PGobject pgObject) {
      return pgObject.getValue();
    }
    return (String) value;
  }

  private record MediaRow(UUID id, String blobKey, String originalFilename, UUID thumbnailOf) {}
}
