// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V49ConnectionIntegrationIdentityMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void v49AddsCatalogLifecycleStateWithoutPersistingRuntimePresence() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToV49(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertThat(jdbc.queryForObject("SELECT count(*) FROM config.integration_type", Integer.class))
          .isZero();

      assertThat(
              jdbc.queryForList(
                  """
                  SELECT column_name
                  FROM information_schema.columns
                  WHERE table_schema = 'config' AND table_name = 'integration_type'
                  """,
                  String.class))
          .containsExactlyInAnyOrder(
              "id", "schema_version", "name", "description", "active", "created_at", "updated_at");
      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT is_nullable, column_default
                  FROM information_schema.columns
                  WHERE table_schema = 'config'
                    AND table_name = 'integration_type'
                    AND column_name = 'active'
                  """))
          .containsEntry("is_nullable", "NO")
          .containsEntry("column_default", "true");
      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT is_nullable, column_default
                  FROM information_schema.columns
                  WHERE table_schema = 'config'
                    AND table_name = 'integration_type'
                    AND column_name = 'updated_at'
                  """))
          .containsEntry("is_nullable", "NO")
          .containsEntry("column_default", "now()");
      jdbc.update(
          """
          INSERT INTO config.integration_type (id, schema_version, name, description)
          VALUES ('efeb9ec0-e54e-4051-a0eb-7668d9c592ac', 1, 'Example', 'Example integration')
          """);

      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT active, created_at IS NOT NULL AS has_created_at,
                         updated_at IS NOT NULL AS has_updated_at
                  FROM config.integration_type
                  WHERE id = 'efeb9ec0-e54e-4051-a0eb-7668d9c592ac'
                    AND schema_version = 1
                  """))
          .containsEntry("active", true)
          .containsEntry("has_created_at", true)
          .containsEntry("has_updated_at", true);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      UPDATE config.integration_type
                      SET active = NULL
                      WHERE id = 'efeb9ec0-e54e-4051-a0eb-7668d9c592ac'
                      """))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      UPDATE config.integration_type
                      SET updated_at = NULL
                      WHERE id = 'efeb9ec0-e54e-4051-a0eb-7668d9c592ac'
                      """))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void v49EnforcesAnOptionalExactIntegrationReference() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToV49(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var integrationId = UUID.fromString("f06cbbc5-0614-462b-af95-67726107c4ef");
      var sourceId = UUID.fromString("5078f069-1d89-4b3d-a2e4-6dd62e83a68a");
      seedDataSource(jdbc, sourceId);
      jdbc.update(
          """
          INSERT INTO config.integration_type (id, schema_version, name, description)
          VALUES (?, 2, 'Example', 'Example integration')
          """,
          integrationId);

      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT integration_id, integration_schema_version
                  FROM profiles.data_source
                  WHERE id = ?
                  """,
                  sourceId))
          .containsEntry("integration_id", null)
          .containsEntry("integration_schema_version", null);

      jdbc.update(
          """
          UPDATE profiles.data_source
          SET integration_id = ?, integration_schema_version = 2
          WHERE id = ?
          """,
          integrationId,
          sourceId);

      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT integration_id, integration_schema_version
                  FROM profiles.data_source
                  WHERE id = ?
                  """,
                  sourceId))
          .containsEntry("integration_id", integrationId)
          .containsEntry("integration_schema_version", 2);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      UPDATE profiles.data_source
                      SET integration_id = NULL, integration_schema_version = 2
                      WHERE id = ?
                      """,
                      sourceId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      UPDATE profiles.data_source
                      SET integration_id = ?, integration_schema_version = NULL
                      WHERE id = ?
                      """,
                      integrationId,
                      sourceId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      UPDATE profiles.data_source
                      SET integration_id = ?, integration_schema_version = 3
                      WHERE id = ?
                      """,
                      integrationId,
                      sourceId))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void v49IndexesNonArchivedUsageByIntegrationIdentity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToV49(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT pg_get_indexdef(index_row.indexrelid, 1, true) AS first_column,
                         pg_get_expr(index_row.indpred, index_row.indrelid, true) AS predicate
                  FROM pg_index index_row
                  JOIN pg_class index_class ON index_class.oid = index_row.indexrelid
                  JOIN pg_namespace index_schema ON index_schema.oid = index_class.relnamespace
                  WHERE index_schema.nspname = 'profiles'
                    AND index_class.relname = 'idx_data_source_non_archived_integration'
                  """))
          .containsEntry("first_column", "integration_id")
          .containsEntry("predicate", "archived_at IS NULL");
    }
  }

  private static void seedDataSource(JdbcTemplate jdbc, UUID sourceId) {
    var delimiterId = UUID.randomUUID();
    var mapperId = UUID.randomUUID();
    var profileId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.data_delimiter (id, name) VALUES (?, ?)",
        delimiterId,
        "delimiter-" + delimiterId);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, mapper)
        VALUES (?, 1, ?, '{}'::jsonb)
        """,
        mapperId,
        "mapper-" + mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_profile
          (id, name, delimiter_id, mapper_id, mapper_version)
        VALUES (?, ?, ?, ?, 1)
        """,
        profileId,
        "profile-" + profileId,
        delimiterId,
        mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_source (id, name, data_profile_id, connection_spec)
        VALUES (?, ?, ?, '{}'::jsonb)
        """,
        sourceId,
        "source-" + sourceId,
        profileId);
  }

  private static void migrateToV49(javax.sql.DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target("49")
        .load()
        .migrate();
  }
}
