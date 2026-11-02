// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class LayerLifecycleMigrationIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String ACTIVE_LAYER_ID = "00000000-0000-0000-0000-000000000101";
  private static final String ARCHIVED_LAYER_ID = "00000000-0000-0000-0000-000000000102";
  private static final String ARCHIVE_USER_ID = "00000000-0000-0000-0000-000000000201";
  private static final String ACTIVE_LAYER_NAME = "Ops";
  private static final String ARCHIVED_LAYER_NAME = "Retired";

  @Test
  void migrationBackfillsLifecycleFromArchiveTimestamp() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "24");
      var jdbc = new JdbcTemplate(dataSource);
      seedUser(jdbc);
      seedLayer(jdbc, ACTIVE_LAYER_ID, ACTIVE_LAYER_NAME, null, null);
      seedLayer(
          jdbc,
          ARCHIVED_LAYER_ID,
          ARCHIVED_LAYER_NAME,
          Timestamp.from(Instant.parse("2026-06-01T12:00:00Z")),
          ARCHIVE_USER_ID);

      migrateAll(dataSource);

      assertThat(readLifecycle(jdbc, ACTIVE_LAYER_ID)).isEqualTo("ACTIVE");
      assertThat(readLifecycle(jdbc, ARCHIVED_LAYER_ID)).isEqualTo("ARCHIVED");
    }
  }

  @Test
  void activeNameIndexExcludesArchivedLifecycleRows() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      seedUser(jdbc);
      seedLayer(jdbc, ACTIVE_LAYER_ID, ACTIVE_LAYER_NAME, null, null);
      seedArchivedLayerWithLifecycle(jdbc);

      assertThatCode(
              () ->
                  seedLayer(
                      jdbc,
                      "00000000-0000-0000-0000-000000000103",
                      ARCHIVED_LAYER_NAME,
                      null,
                      null))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void activeNameIndexExcludesFailedMergeRowsButIncludesPendingMergeRows() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      seedLayerWithLifecycle(
          jdbc, "00000000-0000-0000-0000-000000000301", "Failed merge target", "MERGE_FAILED");

      assertThatCode(
              () ->
                  seedLayer(
                      jdbc,
                      "00000000-0000-0000-0000-000000000302",
                      "Failed merge target",
                      null,
                      null))
          .doesNotThrowAnyException();

      seedLayerWithLifecycle(
          jdbc, "00000000-0000-0000-0000-000000000303", "Pending merge target", "PENDING_MERGE");

      assertThatThrownBy(
              () ->
                  seedLayer(
                      jdbc,
                      "00000000-0000-0000-0000-000000000304",
                      "Pending merge target",
                      null,
                      null))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("uk_layer_name_active");
    }
  }

  private static String readLifecycle(JdbcTemplate jdbc, String layerId) {
    return jdbc.queryForObject(
        "SELECT lifecycle_state::text FROM iam.layer WHERE id = ?::uuid", String.class, layerId);
  }

  private static void seedUser(JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer,
                                  username, email, first_name, last_name, display_name)
        VALUES (?::uuid, ?, ?, 'https://test.issuer', ?, ?, 'F', 'L', 'FL')
        """,
        ARCHIVE_USER_ID,
        "idp-" + ARCHIVE_USER_ID,
        "user-" + ARCHIVE_USER_ID,
        "user-" + ARCHIVE_USER_ID,
        "u" + ARCHIVE_USER_ID + "@test");
  }

  private static void seedLayer(
      JdbcTemplate jdbc, String layerId, String name, Timestamp archivedAt, String archivedBy) {
    jdbc.update(
        """
        INSERT INTO iam.layer (id, name, archived_at, archived_by)
        VALUES (?::uuid, ?, ?, ?::uuid)
        """,
        layerId,
        name,
        archivedAt,
        archivedBy);
  }

  private static void seedArchivedLayerWithLifecycle(JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO iam.layer (id, name, archived_at, archived_by, lifecycle_state)
        VALUES (?::uuid, ?, ?, ?::uuid, 'ARCHIVED'::iam.layer_lifecycle_state)
        """,
        ARCHIVED_LAYER_ID,
        ARCHIVED_LAYER_NAME,
        Timestamp.from(Instant.parse("2026-06-01T12:00:00Z")),
        ARCHIVE_USER_ID);
  }

  private static void seedLayerWithLifecycle(
      JdbcTemplate jdbc, String layerId, String name, String lifecycleState) {
    jdbc.update(
        """
        INSERT INTO iam.layer (id, name, lifecycle_state)
        VALUES (?::uuid, ?, CAST(? AS iam.layer_lifecycle_state))
        """,
        layerId,
        name,
        lifecycleState);
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
}
