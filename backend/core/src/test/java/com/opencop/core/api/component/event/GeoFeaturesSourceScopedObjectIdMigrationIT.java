// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GeoFeaturesSourceScopedObjectIdMigrationIT {

  private static final String OBJECT_ID = "shared-object";
  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void migrate_whenObjectIdExistsInMultipleSources_fansOutRemarksAndEventMedia() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "4");
      var jdbc = new JdbcTemplate(dataSource);

      var sourceA = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
      var sourceB = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
      var remarkId = UUID.randomUUID();
      var mediaId = UUID.randomUUID();
      var userId = UUID.randomUUID();

      insertUser(jdbc, userId);
      insertMedia(jdbc, mediaId, userId);
      insertFeature(
          jdbc, UUID.randomUUID(), OBJECT_ID, sourceA, Instant.parse("2026-03-12T10:00:00Z"));
      insertFeature(
          jdbc, UUID.randomUUID(), OBJECT_ID, sourceB, Instant.parse("2026-03-12T11:00:00Z"));
      jdbc.update(
          "INSERT INTO geo.remarks (id, object_id, content, created_at) VALUES (?, ?, ?, ?)",
          remarkId,
          OBJECT_ID,
          "legacy remark",
          Timestamp.from(Instant.parse("2026-03-12T12:00:00Z")));
      jdbc.update("INSERT INTO event_media (event_id, media_id) VALUES (?, ?)", OBJECT_ID, mediaId);

      migrateAll(dataSource);

      var remarkRows =
          jdbc.queryForList(
              "SELECT id, source_id FROM geo.remarks WHERE object_id = ? ORDER BY source_id",
              OBJECT_ID);
      assertEquals(2, remarkRows.size());
      assertEquals(sourceA, remarkRows.get(0).get("source_id"));
      assertEquals(remarkId, remarkRows.get(0).get("id"));
      assertEquals(sourceB, remarkRows.get(1).get("source_id"));
      assertNotEquals(remarkId, remarkRows.get(1).get("id"));

      var eventMediaSources =
          jdbc.queryForList(
              "SELECT source_id FROM event_attachment WHERE object_id = ? AND attachment_id = ? ORDER BY source_id",
              UUID.class,
              OBJECT_ID,
              mediaId);
      assertEquals(List.of(sourceA, sourceB), eventMediaSources);
    }
  }

  @Test
  void migrate_whenLegacyRowsHaveNoMatchingFeature_skipsThoseRows() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "4");
      var jdbc = new JdbcTemplate(dataSource);
      var orphanMediaId = UUID.randomUUID();
      var userId = UUID.randomUUID();

      insertUser(jdbc, userId);
      insertMedia(jdbc, orphanMediaId, userId);
      jdbc.update(
          "INSERT INTO geo.remarks (id, object_id, content, created_at) VALUES (?, ?, ?, ?)",
          UUID.randomUUID(),
          "orphaned-object",
          "legacy remark",
          Timestamp.from(Instant.parse("2026-03-12T12:00:00Z")));
      jdbc.update(
          "INSERT INTO event_media (event_id, media_id) VALUES (?, ?)",
          "orphaned-object",
          orphanMediaId);

      migrateAll(dataSource);

      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM geo.remarks WHERE object_id = ?",
              Integer.class,
              "orphaned-object"));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM event_attachment WHERE object_id = ?",
              Integer.class,
              "orphaned-object"));
    }
  }

  private static void migrateToVersion(DataSource dataSource, String targetVersion) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(targetVersion)
        .load()
        .migrate();
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

  private static void insertFeature(
      JdbcTemplate jdbc, UUID id, String objectId, UUID sourceId, Instant createdAt) {
    var properties = new OpenCopProperties();
    properties.setObjectId(objectId);
    properties.setSource(sourceId);
    Feature feature = OpenCopPropertiesParser.writeToFeature(properties, null);

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

  private static void insertMedia(JdbcTemplate jdbc, UUID mediaId, UUID userId) {
    jdbc.update(
        """
        INSERT INTO media (
            id, blob_key, original_filename, content_type, size_bytes, access,
            creator_id, owner_id, mime_type, usage
        ) VALUES (?, ?, ?, CAST(? AS media_content_type), ?, CAST(? AS media_access), ?, ?, ?, CAST(? AS media_usage))
        """,
        mediaId,
        "blob/" + mediaId,
        "legacy.png",
        "IMAGE",
        42L,
        "LOGGED_IN",
        userId,
        userId,
        "image/png",
        "EVENT_DETAIL");
  }
}
