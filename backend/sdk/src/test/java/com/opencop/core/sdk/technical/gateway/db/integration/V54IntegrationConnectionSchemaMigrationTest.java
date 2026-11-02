// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V54IntegrationConnectionSchemaMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String PRE_MIGRATION_VERSION = "53";
  private static final String CONNECTION_SCHEMA_VERSION = "54";

  @Test
  void anIntegrationTakesItsDisplayMetadataFromItsHighestSchemaVersion() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var integrationId = UUID.randomUUID();
      seedIdentity(jdbc, integrationId);
      seedSchema(jdbc, integrationId, 1, "Old name", "Old description", false);
      seedSchema(jdbc, integrationId, 2, "Current name", "Current description", false);

      migrate(dataSource, CONNECTION_SCHEMA_VERSION);

      assertThat(
              jdbc.queryForMap(
                  "SELECT name, description, active FROM config.integration WHERE id = ?",
                  integrationId))
          .containsEntry("name", "Current name")
          .containsEntry("description", "Current description")
          .containsEntry("active", false);
      assertThat(
              jdbc.queryForList(
                  """
                  SELECT schema_version FROM config.integration_connection_schema
                   WHERE integration_id = ? ORDER BY schema_version
                  """,
                  Integer.class,
                  integrationId))
          .containsExactly(1, 2);
    }
  }

  @Test
  void theVersionTableKeepsOnlyWhatVersionsTheConnectionContract() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);

      assertThat(
              jdbc.queryForList(
                  """
                  SELECT column_name FROM information_schema.columns
                   WHERE table_schema = 'config' AND table_name = 'integration_connection_schema'
                  """,
                  String.class))
          .containsExactlyInAnyOrder("integration_id", "schema_version", "created_at");
      assertThat(
              jdbc.queryForList(
                  """
                  SELECT conname FROM pg_constraint
                   WHERE conrelid = 'config.integration_connection_schema'::regclass
                  """,
                  String.class))
          .containsExactlyInAnyOrder(
              "integration_connection_schema_pkey",
              "integration_connection_schema_schema_version_check",
              "fk_integration_connection_schema_integration");
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT count(*) FROM information_schema.tables
                   WHERE table_schema = 'config' AND table_name = 'integration_type'
                  """,
                  Integer.class))
          .isZero();
    }
  }

  @Test
  void aConnectionKeepsItsIntegrationUnderTheRenamedSchemaVersionColumn() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var integrationId = UUID.randomUUID();
      seedIdentity(jdbc, integrationId);
      seedSchema(jdbc, integrationId, 1, "Integration", "An integration.", true);
      var mapperId = seedIngressMapper(jdbc);
      var sourceId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO profiles.data_source
            (id, name, provider, access, ingress_mapper_id, integration_id,
             integration_schema_version, connection_spec)
          VALUES (?, ?, 'TCP', 'R', ?, ?, 1, '{}'::jsonb)
          """,
          sourceId,
          "source-" + sourceId,
          mapperId,
          integrationId);
      var updatedAtBefore =
          jdbc.queryForObject(
              "SELECT updated_at FROM profiles.data_source WHERE id = ?", Object.class, sourceId);

      migrate(dataSource, CONNECTION_SCHEMA_VERSION);

      assertThat(
              jdbc.queryForMap(
                  """
                  SELECT integration_id, connection_schema_version, updated_at
                    FROM profiles.data_source WHERE id = ?
                  """,
                  sourceId))
          .containsEntry("integration_id", integrationId)
          .containsEntry("connection_schema_version", 1)
          .containsEntry("updated_at", updatedAtBefore);
    }
  }

  @Test
  void aConnectionCannotNameAnUnknownConnectionSchemaVersion() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO config.integration (id, name, description)
          VALUES (?, 'Integration', 'An integration.')
          """,
          integrationId);
      jdbc.update(
          """
          INSERT INTO config.integration_connection_schema (integration_id, schema_version)
          VALUES (?, 1)
          """,
          integrationId);
      var mapperId = seedIngressMapper(jdbc);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO profiles.data_source
                        (id, name, provider, access, ingress_mapper_id, integration_id,
                         connection_schema_version, connection_spec)
                      VALUES (?, 'source', 'TCP', 'R', ?, ?, 2, '{}'::jsonb)
                      """,
                      UUID.randomUUID(),
                      mapperId,
                      integrationId))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_data_source_connection_schema");
    }
  }

  @Test
  void aConnectionSchemaNeedsItsIntegration() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO config.integration_connection_schema
                        (integration_id, schema_version)
                      VALUES (?, 1)
                      """,
                      UUID.randomUUID()))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_integration_connection_schema_integration");
    }
  }

  @Test
  void anIntegrationNeedsANonBlankName() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO config.integration (id, name, description)
                      VALUES (?, ' ', 'An integration.')
                      """,
                      UUID.randomUUID()))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_integration_name_not_blank");
    }
  }

  @Test
  void schemaVersionsDisagreeingOnActivationAbortTheMigration() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var integrationId = UUID.randomUUID();
      seedIdentity(jdbc, integrationId);
      seedSchema(jdbc, integrationId, 1, "Integration", "An integration.", true);
      seedSchema(jdbc, integrationId, 2, "Integration", "An integration.", false);

      assertThatThrownBy(() -> migrate(dataSource, CONNECTION_SCHEMA_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("disagreeing activation")
          .hasMessageContaining(integrationId.toString());
    }
  }

  @Test
  void anIntegrationWithoutASchemaVersionAbortsTheMigration() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var integrationId = UUID.randomUUID();
      seedIdentity(jdbc, integrationId);

      assertThatThrownBy(() -> migrate(dataSource, CONNECTION_SCHEMA_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("no schema version")
          .hasMessageContaining(integrationId.toString());
    }
  }

  private static JdbcTemplate migratedDatabase(EmbeddedPostgres postgres) {
    var dataSource = postgres.getPostgresDatabase();
    migrate(dataSource, CONNECTION_SCHEMA_VERSION);
    return new JdbcTemplate(dataSource);
  }

  private static void seedIdentity(JdbcTemplate jdbc, UUID integrationId) {
    jdbc.update("INSERT INTO config.integration (id) VALUES (?)", integrationId);
  }

  private static void seedSchema(
      JdbcTemplate jdbc,
      UUID integrationId,
      int schemaVersion,
      String name,
      String description,
      boolean active) {
    jdbc.update(
        """
        INSERT INTO config.integration_type (id, schema_version, name, description, active)
        VALUES (?, ?, ?, ?, ?)
        """,
        integrationId,
        schemaVersion,
        name,
        description,
        active);
  }

  private static UUID seedIngressMapper(JdbcTemplate jdbc) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier)",
        mapperId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, 1, ?, '{}'::jsonb)",
        mapperId,
        "mapper-" + mapperId);
    return mapperId;
  }

  private static void migrate(DataSource dataSource, String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(target)
        .load()
        .migrate();
  }
}
