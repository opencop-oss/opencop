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
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V56ConnectionIntegrationSettingsMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String PRE_MIGRATION_VERSION = "55";
  private static final String SETTINGS_VERSION = "56";
  private static final String SETTINGS = "{\"presence\":{\"callsign\":\"Alpha\"}}";

  @Test
  void storesSettingsForTheConnectionsIntegration() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);

      insertSettings(jdbc, sourceId, integrationId, 1, SETTINGS);

      assertThat(
              jdbc.queryForObject(
                  "SELECT settings::text FROM profiles.connection_integration_settings"
                      + " WHERE source_id = ?",
                  String.class,
                  sourceId))
          .isEqualTo("{\"presence\": {\"callsign\": \"Alpha\"}}");
    }
  }

  @Test
  void rejectsSettingsForAnotherIntegrationThanTheConnections() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = seedIntegration(jdbc, 1);
      var otherIntegrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);

      assertThatThrownBy(() -> insertSettings(jdbc, sourceId, otherIntegrationId, 1, SETTINGS))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_connection_integration_settings_connection");
    }
  }

  @Test
  void deletingTheConnectionDeletesItsSettings() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);
      insertSettings(jdbc, sourceId, integrationId, 1, SETTINGS);

      jdbc.update("DELETE FROM profiles.data_source WHERE id = ?", sourceId);

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM profiles.connection_integration_settings", Integer.class))
          .isZero();
    }
  }

  @Test
  void changingTheIntegrationOfAConnectionWithSettingsFails() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = seedIntegration(jdbc, 1);
      var otherIntegrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);
      insertSettings(jdbc, sourceId, integrationId, 1, SETTINGS);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE profiles.data_source SET integration_id = ? WHERE id = ?",
                      otherIntegrationId,
                      sourceId))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_connection_integration_settings_connection");
    }
  }

  @Test
  void changingTheIntegrationOfAConnectionWithoutSettingsSucceeds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = seedIntegration(jdbc, 1);
      var otherIntegrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);

      jdbc.update(
          "UPDATE profiles.data_source SET integration_id = ? WHERE id = ?",
          otherIntegrationId,
          sourceId);

      assertThat(
              jdbc.queryForObject(
                  "SELECT integration_id FROM profiles.data_source WHERE id = ?",
                  UUID.class,
                  sourceId))
          .isEqualTo(otherIntegrationId);
    }
  }

  @Test
  void rejectsEmptyOrNonObjectSettingsAndSections() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var integrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);

      for (var settings :
          new String[] {
            "{}",
            "[]",
            "\"text\"",
            "{\"presence\":5}",
            "{\"presence\":null}",
            "{\"presence\":[]}",
            "{\"presence\":{\"callsign\":\"Alpha\"},\"heartbeat\":\"off\"}"
          }) {
        assertThatThrownBy(() -> insertSettings(jdbc, sourceId, integrationId, 1, settings))
            .as(settings)
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_connection_integration_settings_object");
      }
    }
  }

  @Test
  void leavesExistingConnectionsUntouched() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var integrationId = seedIntegration(jdbc, 1);
      var sourceId = seedConnection(jdbc, integrationId, 1);
      var before = jdbc.queryForMap("SELECT * FROM profiles.data_source WHERE id = ?", sourceId);

      migrate(dataSource, SETTINGS_VERSION);

      assertThat(jdbc.queryForMap("SELECT * FROM profiles.data_source WHERE id = ?", sourceId))
          .isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM profiles.connection_integration_settings", Integer.class))
          .isZero();
    }
  }

  private static JdbcTemplate migratedDatabase(EmbeddedPostgres postgres) {
    var dataSource = postgres.getPostgresDatabase();
    migrate(dataSource, SETTINGS_VERSION);
    return new JdbcTemplate(dataSource);
  }

  private static UUID seedIntegration(JdbcTemplate jdbc, int schemaVersion) {
    var integrationId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO config.integration (id, name, description) VALUES (?, 'Integration', 'An"
            + " integration.')",
        integrationId);
    jdbc.update(
        "INSERT INTO config.integration_connection_schema (integration_id, schema_version)"
            + " VALUES (?, ?)",
        integrationId,
        schemaVersion);
    return integrationId;
  }

  private static UUID seedConnection(JdbcTemplate jdbc, UUID integrationId, int schemaVersion) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier)",
        mapperId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, 1, ?, '{}'::jsonb)",
        mapperId,
        "mapper-" + mapperId);
    var sourceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, access, ingress_mapper_id, integration_id,
           connection_schema_version, connection_spec)
        VALUES (?, ?, 'TCP', 'R', ?, ?, ?, '{}'::jsonb)
        """,
        sourceId,
        "source-" + sourceId,
        mapperId,
        integrationId,
        schemaVersion);
    return sourceId;
  }

  private static void insertSettings(
      JdbcTemplate jdbc, UUID sourceId, UUID integrationId, int schemaVersion, String settings) {
    jdbc.update(
        """
        INSERT INTO profiles.connection_integration_settings
          (source_id, integration_id, connection_schema_version, settings)
        VALUES (?, ?, ?, ?::jsonb)
        """,
        sourceId,
        integrationId,
        schemaVersion,
        settings);
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
